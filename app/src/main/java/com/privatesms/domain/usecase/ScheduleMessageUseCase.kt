package com.privatesms.domain.usecase

import com.privatesms.domain.model.Message
import com.privatesms.domain.repository.SmsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ScheduleMessageUseCase @Inject constructor(
    private val smsRepository: SmsRepository
) {
    fun getScheduledMessages(): Flow<List<Message>> {
        return smsRepository.getScheduledMessages()
    }

    suspend fun schedule(address: String, body: String, scheduledTime: Long) {
        smsRepository.scheduleSms(address, body, scheduledTime)
    }

    suspend fun cancel(messageId: Long) {
        smsRepository.cancelScheduledSms(messageId)
    }
}
