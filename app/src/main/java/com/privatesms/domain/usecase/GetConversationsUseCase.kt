package com.privatesms.domain.usecase

import com.privatesms.domain.model.Conversation
import com.privatesms.domain.repository.SmsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetConversationsUseCase @Inject constructor(
    private val smsRepository: SmsRepository
) {
    operator fun invoke(archived: Boolean = false, isPrivate: Boolean = false): Flow<List<Conversation>> {
        return smsRepository.getConversations(archived, isPrivate)
    }
}
