package com.privatesms.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.privatesms.data.db.DatabaseManager
import com.privatesms.data.model.ConversationEntity
import com.privatesms.data.model.MessageEntity
import com.privatesms.service.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var databaseManager: DatabaseManager

    @Inject
    lateinit var notificationHelper: NotificationHelper

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) {
            val mimeType = intent.type
            if (mimeType == "application/vnd.wap.mms-message") {
                val pdu = intent.getByteArrayExtra("data") ?: return
                // Process MMS PDU data
                Log.d("MmsReceiver", "MMS PDU received of size: ${pdu.size}")

                coroutineScope.launch {
                    processIncomingMms(context)
                }
            }
        }
    }

    private suspend fun processIncomingMms(context: Context) {
        if (!databaseManager.isUnlocked.value) {
            // DB is locked, queue generic MMS
            databaseManager.queuePendingSms("MMS Sender", "New MMS image received", System.currentTimeMillis(), MessageEntity.TYPE_RECEIVED)
            notificationHelper.showSecureLockedNotification(context)
            return
        }

        try {
            val db = databaseManager.getDatabase()
            val sender = "MMS Sender" // Placeholder or read from PDU
            val body = "Received a multimedia message"
            val timestamp = System.currentTimeMillis()
            val threadId = sender.hashCode().toLong()

            if (db.blocklistDao().isNumberBlocked(sender)) {
                return
            }

            val msg = MessageEntity(
                threadId = threadId,
                address = sender,
                body = body,
                type = MessageEntity.TYPE_RECEIVED,
                date = timestamp,
                dateSent = timestamp,
                read = false,
                status = MessageEntity.STATUS_NONE,
                isScheduled = false,
                attachmentPath = null // Real MMS would parse attachment file path from part content provider
            )
            db.messageDao().insertMessage(msg)

            val existing = db.conversationDao().getConversationByThreadId(threadId)
            db.conversationDao().insertConversation(
                ConversationEntity(
                    threadId = threadId,
                    address = sender,
                    snippet = body,
                    date = timestamp,
                    read = false,
                    archived = existing?.archived ?: false,
                    isPrivate = existing?.isPrivate ?: false,
                    messageCount = (existing?.messageCount ?: 0) + 1,
                    isSpam = existing?.isSpam ?: false
                )
            )

            notificationHelper.showNotification(context, sender, body, threadId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
