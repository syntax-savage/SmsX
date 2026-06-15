package com.privatesms.domain.usecase

import com.privatesms.domain.repository.BlocklistRepository
import javax.inject.Inject

class BlockNumberUseCase @Inject constructor(
    private val blocklistRepository: BlocklistRepository
) {
    suspend fun block(phoneNumber: String, reason: String? = null) {
        blocklistRepository.blockNumber(phoneNumber, reason)
    }

    suspend fun unblock(phoneNumber: String) {
        blocklistRepository.unblockNumber(phoneNumber)
    }
}
