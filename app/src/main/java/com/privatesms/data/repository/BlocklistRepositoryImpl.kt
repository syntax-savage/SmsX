package com.privatesms.data.repository

import com.privatesms.data.db.DatabaseManager
import com.privatesms.data.model.BlockedNumberEntity
import com.privatesms.domain.repository.BlocklistRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlocklistRepositoryImpl @Inject constructor(
    private val databaseManager: DatabaseManager
) : BlocklistRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getBlockedNumbers(): Flow<List<BlockedNumberEntity>> {
        return databaseManager.isUnlocked.flatMapLatest { unlocked ->
            if (unlocked) {
                databaseManager.getDatabase().blocklistDao().getBlockedNumbers()
            } else {
                flowOf(emptyList())
            }
        }
    }

    override suspend fun blockNumber(phoneNumber: String, reason: String?) {
        val db = databaseManager.getDatabase()
        val blockedNumber = BlockedNumberEntity(
            phoneNumber = phoneNumber,
            dateBlocked = System.currentTimeMillis(),
            reason = reason
        )
        db.blocklistDao().insertBlockedNumber(blockedNumber)
    }

    override suspend fun unblockNumber(phoneNumber: String) {
        val db = databaseManager.getDatabase()
        db.blocklistDao().deleteBlockedNumber(phoneNumber)
    }

    override suspend fun unblockNumberById(id: Long) {
        val db = databaseManager.getDatabase()
        db.blocklistDao().deleteBlockedNumberById(id)
    }

    override suspend fun isNumberBlocked(phoneNumber: String): Boolean {
        if (!databaseManager.isUnlocked.value) return false
        val db = databaseManager.getDatabase()
        return db.blocklistDao().isNumberBlocked(phoneNumber)
    }
}
