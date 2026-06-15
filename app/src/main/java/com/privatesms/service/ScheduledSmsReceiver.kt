package com.privatesms.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class ScheduledSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra("message_id", -1L)
        if (messageId == -1L) return

        val data = Data.Builder()
            .putLong("message_id", messageId)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ScheduledSmsWorker>()
            .setInputData(data)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueue(workRequest)
    }
}
