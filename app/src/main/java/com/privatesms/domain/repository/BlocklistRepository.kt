package com.privatesms.domain.repository

import com.privatesms.data.model.BlockedNumberEntity
import kotlinx.coroutines.flow.Flow

interface BlocklistRepository {
    fun getBlockedNumbers(): Flow<List<BlockedNumberEntity>>
    suspend fun blockNumber(phoneNumber: String, reason: String? = null)
    suspend fun unblockNumber(phoneNumber: String)
    suspend fun unblockNumberById(id: Long)
    suspend fun isNumberBlocked(phoneNumber: String): Boolean
}
