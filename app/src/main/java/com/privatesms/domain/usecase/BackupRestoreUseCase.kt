package com.privatesms.domain.usecase

import android.content.Context
import android.os.Environment
import com.privatesms.data.db.DatabaseManager
import com.privatesms.data.model.BlockedNumberEntity
import com.privatesms.data.model.ConversationEntity
import com.privatesms.data.model.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import javax.inject.Inject

class BackupRestoreUseCase @Inject constructor(
    private val context: Context,
    private val databaseManager: DatabaseManager
) {

    suspend fun exportBackup(): String? = withContext(Dispatchers.IO) {
        if (!databaseManager.isUnlocked.value) return@withContext null
        try {
            val db = databaseManager.getDatabase()
            
            // Fetch conversations
            val conversations = db.conversationDao().getConversations(archived = false, isPrivate = false).first() +
                    db.conversationDao().getConversations(archived = true, isPrivate = false).first() +
                    db.conversationDao().getConversations(archived = false, isPrivate = true).first()
            
            val backupJson = JSONObject()
            val conversationsArray = JSONArray()

            conversations.forEach { conv ->
                val convJson = JSONObject().apply {
                    put("threadId", conv.threadId)
                    put("address", conv.address)
                    put("snippet", conv.snippet)
                    put("date", conv.date)
                    put("read", conv.read)
                    put("archived", conv.archived)
                    put("isPrivate", conv.isPrivate)
                    put("messageCount", conv.messageCount)
                    put("isSpam", conv.isSpam)
                }

                val messages = db.messageDao().getMessagesForThread(conv.threadId).first()
                val messagesArray = JSONArray()
                messages.forEach { msg ->
                    val msgJson = JSONObject().apply {
                        put("id", msg.id)
                        put("threadId", msg.threadId)
                        put("address", msg.address)
                        put("body", msg.body)
                        put("type", msg.type)
                        put("date", msg.date)
                        put("dateSent", msg.dateSent)
                        put("read", msg.read)
                        put("status", msg.status)
                        put("isScheduled", msg.isScheduled)
                        put("scheduledTime", msg.scheduledTime ?: 0L)
                        put("isEncrypted", msg.isEncrypted)
                        put("attachmentPath", msg.attachmentPath ?: "")
                    }
                    messagesArray.put(msgJson)
                }
                convJson.put("messages", messagesArray)
                conversationsArray.put(convJson)
            }

            backupJson.put("conversations", conversationsArray)

            // Blocked list
            val blockedNumbers = db.blocklistDao().getBlockedNumbersSync()
            val blockedArray = JSONArray()
            blockedNumbers.forEach { block ->
                val blockJson = JSONObject().apply {
                    put("phoneNumber", block.phoneNumber)
                    put("dateBlocked", block.dateBlocked)
                    put("reason", block.reason ?: "")
                }
                blockedArray.put(blockJson)
            }
            backupJson.put("blockedNumbers", blockedArray)

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val privateSmsDir = File(downloadsDir, "PrivateSMS")
            if (!privateSmsDir.exists()) {
                privateSmsDir.mkdirs()
            }
            val file = File(privateSmsDir, "backup_${System.currentTimeMillis()}.json")
            file.writeText(backupJson.toString())
            return@withContext file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun importBackup(inputStream: InputStream): Boolean = withContext(Dispatchers.IO) {
        if (!databaseManager.isUnlocked.value) return@withContext false
        try {
            val db = databaseManager.getDatabase()
            val jsonText = inputStream.bufferedReader().use { it.readText() }
            val backupJson = JSONObject(jsonText)

            val conversationsArray = backupJson.optJSONArray("conversations")
            if (conversationsArray != null) {
                for (i in 0 until conversationsArray.length()) {
                    val convJson = conversationsArray.getJSONObject(i)
                    val threadId = convJson.getLong("threadId")
                    val address = convJson.getString("address")
                    val snippet = convJson.getString("snippet")
                    val date = convJson.getLong("date")
                    val read = convJson.getBoolean("read")
                    val archived = convJson.optBoolean("archived", false)
                    val isPrivate = convJson.optBoolean("isPrivate", false)
                    val messageCount = convJson.optInt("messageCount", 0)
                    val isSpam = convJson.optBoolean("isSpam", false)

                    val conversation = ConversationEntity(
                        threadId = threadId,
                        address = address,
                        snippet = snippet,
                        date = date,
                        read = read,
                        archived = archived,
                        isPrivate = isPrivate,
                        messageCount = messageCount,
                        isSpam = isSpam
                    )
                    db.conversationDao().insertConversation(conversation)

                    val messagesArray = convJson.optJSONArray("messages")
                    if (messagesArray != null) {
                        val messagesToInsert = mutableListOf<MessageEntity>()
                        for (j in 0 until messagesArray.length()) {
                            val msgJson = messagesArray.getJSONObject(j)
                            val body = msgJson.getString("body")
                            val type = msgJson.getInt("type")
                            val msgDate = msgJson.getLong("date")
                            val dateSent = msgJson.optLong("dateSent", msgDate)
                            val msgRead = msgJson.getBoolean("read")
                            val status = msgJson.optInt("status", MessageEntity.STATUS_NONE)
                            val isScheduled = msgJson.optBoolean("isScheduled", false)
                            val scheduledTime = if (msgJson.has("scheduledTime")) msgJson.getLong("scheduledTime") else null
                            val isEncrypted = msgJson.optBoolean("isEncrypted", true)
                            val attachmentPath = msgJson.optString("attachmentPath", "").takeIf { it.isNotEmpty() }

                            messagesToInsert.add(
                                MessageEntity(
                                    threadId = threadId,
                                    address = address,
                                    body = body,
                                    type = type,
                                    date = msgDate,
                                    dateSent = dateSent,
                                    read = msgRead,
                                    status = status,
                                    isScheduled = isScheduled,
                                    scheduledTime = scheduledTime,
                                    isEncrypted = isEncrypted,
                                    attachmentPath = attachmentPath
                                )
                            )
                        }
                        db.messageDao().insertMessages(messagesToInsert)
                    }
                }
            }

            // Restore Blocked Numbers
            val blockedArray = backupJson.optJSONArray("blockedNumbers")
            if (blockedArray != null) {
                for (i in 0 until blockedArray.length()) {
                    val blockJson = blockedArray.getJSONObject(i)
                    val phoneNumber = blockJson.getString("phoneNumber")
                    val dateBlocked = blockJson.getLong("dateBlocked")
                    val reason = blockJson.optString("reason", "").takeIf { it.isNotEmpty() }

                    db.blocklistDao().insertBlockedNumber(
                        BlockedNumberEntity(
                            phoneNumber = phoneNumber,
                            dateBlocked = dateBlocked,
                            reason = reason
                        )
                    )
                }
            }
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
}
