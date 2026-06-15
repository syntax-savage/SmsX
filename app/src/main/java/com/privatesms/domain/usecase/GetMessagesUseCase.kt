package com.privatesms.domain.usecase

import com.privatesms.domain.model.Message
import com.privatesms.domain.repository.SmsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMessagesUseCase @Inject constructor(
    private val smsRepository: SmsRepository
) {
    operator fun invoke(threadId: Long): Flow<List<Message>> {
        return smsRepository.getMessagesForThread(threadId)
    }
}
