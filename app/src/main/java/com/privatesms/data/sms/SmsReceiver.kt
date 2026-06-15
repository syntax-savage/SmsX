package com.privatesms.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.privatesms.data.settingsDataStore
import com.privatesms.data.db.DatabaseManager
import com.privatesms.data.model.ConversationEntity
import com.privatesms.data.model.MessageEntity
import com.privatesms.service.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

// Use shared settingsDataStore

@AndroidEntryPoint
open class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var databaseManager: DatabaseManager

    @Inject
    lateinit var notificationHelper: NotificationHelper

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private val PREF_SPAM_KEYWORDS = stringSetPreferencesKey("spam_keywords")
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Telephony.Sms.Intents.SMS_DELIVER_ACTION || action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            if (messages.isEmpty()) return

            val sender = messages[0].displayOriginatingAddress ?: return
            val body = messages.joinToString("") { it.displayMessageBody ?: "" }
            val timestamp = messages[0].timestampMillis

            coroutineScope.launch {
                processIncomingSms(context, sender, body, timestamp)
            }
        }
    }

    private suspend fun processIncomingSms(context: Context, sender: String, body: String, timestamp: Long) {
        if (!databaseManager.isUnlocked.value) {
            // DB is locked, queue the SMS for later processing
            databaseManager.queuePendingSms(sender, body, timestamp, MessageEntity.TYPE_RECEIVED)
            // Show generic secure notification
            notificationHelper.showSecureLockedNotification(context)
            return
        }

        try {
            val db = databaseManager.getDatabase()
            
            // 1. Check if number is blocked
            if (db.blocklistDao().isNumberBlocked(sender)) {
                Log.d("SmsReceiver", "Discarded message from blocked number: $sender")
                return
            }

            // 2. Check keyword spam filter
            val prefs = context.settingsDataStore.data.first()
            val keywords = prefs[PREF_SPAM_KEYWORDS] ?: emptySet()
            val isSpam = keywords.any { body.contains(it, ignoreCase = true) }

            val threadId = sender.hashCode().toLong()

            // 3. Save message to DB
            val msgEntity = MessageEntity(
                threadId = threadId,
                address = sender,
                body = body,
                type = MessageEntity.TYPE_RECEIVED,
                date = timestamp,
                dateSent = timestamp,
                read = false,
                status = MessageEntity.STATUS_NONE,
                isScheduled = false
            )
            db.messageDao().insertMessage(msgEntity)

            // 4. Update conversation
            val existing = db.conversationDao().getConversationByThreadId(threadId)
            val count = (existing?.messageCount ?: 0) + 1
            
            val conversation = ConversationEntity(
                threadId = threadId,
                address = sender,
                snippet = body,
                date = timestamp,
                read = false,
                archived = existing?.archived ?: isSpam, // Auto-archive if spam
                isPrivate = existing?.isPrivate ?: false,
                messageCount = count,
                isSpam = existing?.isSpam ?: isSpam
            )
            db.conversationDao().insertConversation(conversation)

            // 5. Trigger notification (if not spam)
            if (!isSpam) {
                notificationHelper.showNotification(context, sender, body, threadId)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: if database error occurred, queue to avoid data loss
            databaseManager.queuePendingSms(sender, body, timestamp, MessageEntity.TYPE_RECEIVED)
        }
    }

    // Nested receiver for SMS_RECEIVED (non-default SMS handler)
    @AndroidEntryPoint
    class SmsReceivedReceiver : SmsReceiver()
}
