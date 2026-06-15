package com.privatesms.data.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.privatesms.data.db.DatabaseManager
import com.privatesms.data.model.MessageEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmsStatusReceiver : BroadcastReceiver() {

    @Inject
    lateinit var databaseManager: DatabaseManager

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val dataUri = intent.data ?: return
        // Uri format is sms_id://{id}
        val messageId = dataUri.schemeSpecificPart.substringAfter("//").toLongOrNull() ?: return

        coroutineScope.launch {
            if (!databaseManager.isUnlocked.value) return@launch
            try {
                val db = databaseManager.getDatabase()
                val message = db.messageDao().getMessageById(messageId) ?: return@launch

                val newStatus = when (action) {
                    "com.privatesms.SMS_SENT" -> {
                        if (resultCode == Activity.RESULT_OK) {
                            MessageEntity.STATUS_SENT
                        } else {
                            MessageEntity.STATUS_FAILED
                        }
                    }
                    "com.privatesms.SMS_DELIVERED" -> MessageEntity.STATUS_DELIVERED
                    else -> message.status
                }

                db.messageDao().updateMessage(message.copy(status = newStatus))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
