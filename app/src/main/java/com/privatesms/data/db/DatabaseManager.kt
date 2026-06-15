package com.privatesms.data.db

import android.content.Context
import android.provider.Settings
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.privatesms.data.model.ConversationEntity
import com.privatesms.data.model.MessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.privatesms.data.settingsDataStore

class DatabaseManager(private val context: Context) {
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked

    private var dbInstance: AppDatabase? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val pendingFile = File(context.noBackupFilesDir, "pending_sms.json")

    companion object {
        private val PREF_PIN_HASH = stringPreferencesKey("pin_hash")
        private val PREF_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        private val PREF_ENCRYPTED_PIN = stringPreferencesKey("encrypted_pin") // stores PIN encrypted by KeyStore for auto-unlock
    }

    init {
        // Initialize SQLCipher libraries
        SQLiteDatabase.loadLibs(context)
        
        coroutineScope.launch {
            val prefs = context.settingsDataStore.data.first()
            val hasPin = prefs[PREF_PIN_HASH] != null
            val lockEnabled = prefs[PREF_APP_LOCK_ENABLED] ?: false
            
            if (!hasPin) {
                // First run, unlock with default pin derived from ANDROID_ID
                val androidId = getAndroidId()
                val key = CryptoUtils.deriveKey("default_first_run_pin_12345", androidId)
                try {
                    openDatabase(key)
                    _isUnlocked.value = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (!lockEnabled) {
                // Lock is disabled, try to auto-unlock using the Keystore-encrypted PIN
                val encPin = prefs[PREF_ENCRYPTED_PIN]
                if (encPin != null) {
                    try {
                        val pin = KeyStoreHelper.decrypt(encPin)
                        val androidId = getAndroidId()
                        val key = CryptoUtils.deriveKey(pin, androidId)
                        openDatabase(key)
                        _isUnlocked.value = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun getDatabase(): AppDatabase {
        return dbInstance ?: throw IllegalStateException("Database is locked. Enter PIN first.")
    }

    private fun getAndroidId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "default_salt_1298"
    }

    @Synchronized
    fun unlock(pin: String): Boolean {
        return try {
            val androidId = getAndroidId()
            val key = CryptoUtils.deriveKey(pin, androidId)
            openDatabase(key)
            _isUnlocked.value = true
            processPendingQueue()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @Synchronized
    private fun openDatabase(key: ByteArray) {
        if (dbInstance == null) {
            val factory = SupportFactory(key)
            dbInstance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "private_sms.db"
            )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
        }
    }

    @Synchronized
    fun lock() {
        dbInstance?.close()
        dbInstance = null
        _isUnlocked.value = false
    }

    @Synchronized
    fun rekeyDatabase(oldPin: String, newPin: String): Boolean {
        val db = dbInstance ?: return false
        return try {
            val androidId = getAndroidId()
            val newKey = CryptoUtils.deriveKey(newPin, androidId)
            
            // To rekey SQLCipher database, we use raw command
            val supportDb = db.openHelper.writableDatabase
            // SupportSQLiteDatabase doesn't expose raw rekey directly, but SQLCipher's SQLiteDatabase does.
            // We can execute: PRAGMA rekey = '...'
            // In SQLCipher, rekey can be executed as:
            // supportDb.execSQL("PRAGMA rekey = '" + newKeyHex + "'")
            // Wait, SQLCipher allows passing the raw key as a hex-encoded string or text.
            // A common way is to pass key bytes in Hex format: x'hex'
            val hexKey = newKey.joinToString("") { "%02x".format(it) }
            supportDb.execSQL("PRAGMA rekey = \"x'$hexKey'\"")
            
            // Close and reopen database with the new key to verify
            db.close()
            dbInstance = null
            openDatabase(newKey)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Rekey database when setting up PIN for the first time
    @Synchronized
    fun setupInitialPin(newPin: String): Boolean {
        return try {
            val androidId = getAndroidId()
            val defaultKey = CryptoUtils.deriveKey("default_first_run_pin_12345", androidId)
            
            // Ensure DB is open with default key first
            openDatabase(defaultKey)
            
            val db = dbInstance ?: return false
            val newKey = CryptoUtils.deriveKey(newPin, androidId)
            val supportDb = db.openHelper.writableDatabase
            val hexKey = newKey.joinToString("") { "%02x".format(it) }
            supportDb.execSQL("PRAGMA rekey = \"x'$hexKey'\"")
            
            db.close()
            dbInstance = null
            openDatabase(newKey)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            // If it failed because database was already created with another key or not yet open, try standard open
            false
        }
    }

    fun queuePendingSms(address: String, body: String, date: Long, type: Int) {
        synchronized(pendingFile) {
            try {
                val array = if (pendingFile.exists()) {
                    JSONArray(pendingFile.readText())
                } else {
                    JSONArray()
                }
                val obj = JSONObject().apply {
                    put("address", address)
                    put("body", body)
                    put("date", date)
                    put("type", type)
                }
                array.put(obj)
                pendingFile.writeText(array.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun processPendingQueue() {
        coroutineScope.launch {
            val db = dbInstance ?: return@launch
            val jsonText = synchronized(pendingFile) {
                if (pendingFile.exists()) {
                    val text = pendingFile.readText()
                    pendingFile.delete()
                    text
                } else {
                    null
                }
            }
            if (jsonText.isNullOrEmpty()) return@launch
            
            try {
                val array = JSONArray(jsonText)
                val messagesToInsert = mutableListOf<MessageEntity>()
                
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val address = obj.getString("address")
                    val body = obj.getString("body")
                    val date = obj.getLong("date")
                    val type = obj.getInt("type")
                    val threadId = address.hashCode().toLong()

                    messagesToInsert.add(
                        MessageEntity(
                            threadId = threadId,
                            address = address,
                            body = body,
                            type = type,
                            date = date,
                            dateSent = date,
                            read = false,
                            status = MessageEntity.STATUS_NONE,
                            isScheduled = false
                        )
                    )
                }

                if (messagesToInsert.isNotEmpty()) {
                    db.messageDao().insertMessages(messagesToInsert)
                    
                    val grouped = messagesToInsert.groupBy { it.threadId }
                    grouped.forEach { (threadId, msgs) ->
                        val latest = msgs.maxByOrNull { it.date }!!
                        val existing = db.conversationDao().getConversationByThreadId(threadId)
                        val count = (existing?.messageCount ?: 0) + msgs.size
                        db.conversationDao().insertConversation(
                            ConversationEntity(
                                threadId = threadId,
                                address = latest.address,
                                snippet = latest.body,
                                date = latest.date,
                                read = false,
                                archived = existing?.archived ?: false,
                                isPrivate = existing?.isPrivate ?: false,
                                messageCount = count
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
