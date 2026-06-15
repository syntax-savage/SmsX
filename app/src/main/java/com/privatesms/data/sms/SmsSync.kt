package com.privatesms.data.sms

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.privatesms.data.db.DatabaseManager
import com.privatesms.data.model.ConversationEntity
import com.privatesms.data.model.MessageEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseManager: DatabaseManager
) {
    private val _syncProgress = MutableStateFlow(0f)
    val syncProgress: StateFlow<Float> = _syncProgress

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    suspend fun syncAll() = withContext(Dispatchers.IO) {
        if (_isSyncing.value) return@withContext
        if (!databaseManager.isUnlocked.value) return@withContext

        _isSyncing.value = true
        _syncProgress.value = 0f

        try {
            val db = databaseManager.getDatabase()
            val contentResolver = context.contentResolver

            // 1. Sync SMS
            val smsList = mutableListOf<MessageEntity>()
            val smsCursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.THREAD_ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.DATE,
                    Telephony.Sms.DATE_SENT,
                    Telephony.Sms.READ,
                    Telephony.Sms.STATUS
                ),
                null,
                null,
                null
            )

            smsCursor?.use { cursor ->
                val threadIdIdx = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                val dateSentIdx = cursor.getColumnIndex(Telephony.Sms.DATE_SENT)
                val readIdx = cursor.getColumnIndex(Telephony.Sms.READ)
                val statusIdx = cursor.getColumnIndex(Telephony.Sms.STATUS)

                val total = cursor.count
                var count = 0

                while (cursor.moveToNext()) {
                    val threadId = if (threadIdIdx != -1) cursor.getLong(threadIdIdx) else 0L
                    val address = if (addressIdx != -1) cursor.getString(addressIdx) ?: "" else ""
                    val body = if (bodyIdx != -1) cursor.getString(bodyIdx) ?: "" else ""
                    val type = if (typeIdx != -1) cursor.getInt(typeIdx) else 1
                    val date = if (dateIdx != -1) cursor.getLong(dateIdx) else System.currentTimeMillis()
                    val dateSent = if (dateSentIdx != -1) cursor.getLong(dateSentIdx) else date
                    val read = if (readIdx != -1) cursor.getInt(readIdx) == 1 else true
                    val statusValue = if (statusIdx != -1) cursor.getInt(statusIdx) else -1

                    val mappedType = if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                        MessageEntity.TYPE_RECEIVED
                    } else {
                        MessageEntity.TYPE_SENT
                    }

                    val mappedStatus = when (statusValue) {
                        Telephony.Sms.STATUS_COMPLETE -> MessageEntity.STATUS_DELIVERED
                        Telephony.Sms.STATUS_FAILED -> MessageEntity.STATUS_FAILED
                        Telephony.Sms.STATUS_PENDING -> MessageEntity.STATUS_SENT
                        else -> MessageEntity.STATUS_NONE
                    }

                    smsList.add(
                        MessageEntity(
                            threadId = threadId,
                            address = address,
                            body = body,
                            type = mappedType,
                            date = date,
                            dateSent = dateSent,
                            read = read,
                            status = mappedStatus,
                            isScheduled = false
                        )
                    )

                    count++
                    if (count % 100 == 0 && total > 0) {
                        _syncProgress.value = (count.toFloat() / total) * 0.7f
                    }
                }
            }

            // Write SMS in batch chunks
            if (smsList.isNotEmpty()) {
                db.messageDao().insertMessages(smsList)
            }

            // 2. Sync MMS
            val mmsList = mutableListOf<MessageEntity>()
            val mmsCursor = contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(
                    Telephony.Mms._ID,
                    Telephony.Mms.THREAD_ID,
                    Telephony.Mms.DATE,
                    Telephony.Mms.READ
                ),
                null,
                null,
                null
            )

            mmsCursor?.use { cursor ->
                val idIdx = cursor.getColumnIndex(Telephony.Mms._ID)
                val threadIdIdx = cursor.getColumnIndex(Telephony.Mms.THREAD_ID)
                val dateIdx = cursor.getColumnIndex(Telephony.Mms.DATE)
                val readIdx = cursor.getColumnIndex(Telephony.Mms.READ)

                val total = cursor.count
                var count = 0

                while (cursor.moveToNext()) {
                    val mmsId = if (idIdx != -1) cursor.getLong(idIdx) else -1L
                    val threadId = if (threadIdIdx != -1) cursor.getLong(threadIdIdx) else 0L
                    val dateSeconds = if (dateIdx != -1) cursor.getLong(dateIdx) else 0L
                    val dateMs = dateSeconds * 1000
                    val read = if (readIdx != -1) cursor.getInt(readIdx) == 1 else true

                    if (mmsId != -1L) {
                        val address = getMmsAddress(contentResolver, mmsId)
                        val (body, imagePath) = getMmsContent(contentResolver, mmsId)

                        mmsList.add(
                            MessageEntity(
                                threadId = threadId,
                                address = address,
                                body = body,
                                type = MessageEntity.TYPE_RECEIVED,
                                date = dateMs,
                                dateSent = dateMs,
                                read = read,
                                status = MessageEntity.STATUS_NONE,
                                isScheduled = false,
                                attachmentPath = imagePath
                            )
                        )
                    }

                    count++
                    if (total > 0) {
                        _syncProgress.value = 0.7f + (count.toFloat() / total) * 0.2f
                    }
                }
            }

            if (mmsList.isNotEmpty()) {
                db.messageDao().insertMessages(mmsList)
            }

            // 3. Build Conversations summary from synced messages
            val conversationsToInsert = mutableListOf<ConversationEntity>()
            val groupedByThread = (smsList + mmsList).groupBy { it.threadId }
            groupedByThread.forEach { (threadId, messages) ->
                if (threadId != 0L) {
                    val latestMsg = messages.maxByOrNull { it.date }!!
                    conversationsToInsert.add(
                        ConversationEntity(
                            threadId = threadId,
                            address = latestMsg.address,
                            snippet = latestMsg.body,
                            date = latestMsg.date,
                            read = messages.all { it.read },
                            archived = false,
                            isPrivate = false,
                            messageCount = messages.size,
                            isSpam = false
                        )
                    )
                }
            }

            if (conversationsToInsert.isNotEmpty()) {
                db.conversationDao().insertConversations(conversationsToInsert)
            }

            _syncProgress.value = 1.0f
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isSyncing.value = false
        }
    }

    private fun getMmsAddress(contentResolver: ContentResolver, mmsId: Long): String {
        val uri = Uri.parse("content://mms/$mmsId/addr")
        val cursor = contentResolver.query(
            uri,
            arrayOf("address", "type"),
            "type=137", // 137 = FROM address
            null,
            null
        )
        var address = ""
        cursor?.use {
            if (it.moveToFirst()) {
                address = it.getString(0) ?: ""
            }
        }
        return address
    }

    private fun getMmsContent(contentResolver: ContentResolver, mmsId: Long): Pair<String, String?> {
        val uri = Uri.parse("content://mms/part")
        val cursor = contentResolver.query(
            uri,
            arrayOf("_id", "mid", "ct", "text", "_data"),
            "mid = ?",
            arrayOf(mmsId.toString()),
            null
        )
        var body = ""
        var attachmentPath: String? = null

        cursor?.use {
            val ctIdx = it.getColumnIndex("ct")
            val textIdx = it.getColumnIndex("text")
            val idIdx = it.getColumnIndex("_id")

            while (it.moveToNext()) {
                val ct = if (ctIdx != -1) it.getString(ctIdx) ?: "" else ""
                if (ct == "text/plain") {
                    body = if (textIdx != -1) it.getString(textIdx) ?: "" else ""
                } else if (ct.startsWith("image/")) {
                    val partId = if (idIdx != -1) it.getLong(idIdx) else -1L
                    if (partId != -1L) {
                        val partUri = Uri.parse("content://mms/part/$partId")
                        attachmentPath = saveMmsPartToPrivateStorage(contentResolver, partUri)
                    }
                }
            }
        }
        return Pair(body, attachmentPath)
    }

    private fun saveMmsPartToPrivateStorage(contentResolver: ContentResolver, partUri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(partUri) ?: return null
            val mmsDir = File(context.filesDir, "mms")
            if (!mmsDir.exists()) mmsDir.mkdirs()
            val file = File(mmsDir, "mms_part_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
