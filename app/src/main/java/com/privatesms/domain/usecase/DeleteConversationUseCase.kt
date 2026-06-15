package com.privatesms.domain.usecase

import com.privatesms.domain.repository.SmsRepository
import javax.inject.Inject

class DeleteConversationUseCase @Inject constructor(
    private val smsRepository: SmsRepository
) {
    suspend operator fun invoke(threadId: Long) {
        smsRepository.deleteConversation(threadId)
    }
}
