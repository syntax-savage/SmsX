package com.privatesms.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.privatesms.domain.repository.SmsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var smsRepository: SmsRepository

    @Inject
    lateinit var notificationHelper: NotificationHelper

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val threadId = intent.getLongExtra(NotificationHelper.EXTRA_THREAD_ID, -1L)
        if (threadId == -1L) return

        when (action) {
            NotificationHelper.ACTION_REPLY -> {
                val replyBundle = RemoteInput.getResultsFromIntent(intent)
                val replyText = replyBundle?.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)?.toString()
                val senderNumber = intent.getStringExtra("sender_number")
                
                if (!replyText.isNullOrEmpty() && !senderNumber.isNullOrEmpty()) {
                    coroutineScope.launch {
                        try {
                            smsRepository.sendSms(senderNumber, replyText, null)
                            smsRepository.setReadStatus(threadId, true)
                            notificationHelper.cancelNotification(threadId)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            NotificationHelper.ACTION_MARK_AS_READ -> {
                coroutineScope.launch {
                    try {
                        smsRepository.setReadStatus(threadId, true)
                        notificationHelper.cancelNotification(threadId)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}
