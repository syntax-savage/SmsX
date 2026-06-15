package com.privatesms.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.privatesms.data.db.DatabaseManager
import com.privatesms.data.model.MessageEntity
import com.privatesms.data.sms.SmsSender
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class ScheduledSmsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val databaseManager: DatabaseManager,
    private val smsSender: SmsSender
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val messageId = inputData.getLong("message_id", -1L)
        if (messageId == -1L) return@withContext Result.failure()

        if (!databaseManager.isUnlocked.value) {
            // Retry later if the database is currently locked
            return@withContext Result.retry()
        }

        try {
            val db = databaseManager.getDatabase()
            val message = db.messageDao().getMessageById(messageId) ?: return@withContext Result.failure()

            if (message.isScheduled && message.status != MessageEntity.STATUS_SENT) {
                smsSender.sendSms(message.address, message.body, message.id, null)
                db.messageDao().updateMessage(message.copy(
                    isScheduled = false,
                    status = MessageEntity.STATUS_SENT
                ))
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
