package com.privatesms.data.db

import androidx.room.*
import com.privatesms.data.model.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations WHERE archived = :archived AND isPrivate = :isPrivate ORDER BY date DESC")
    fun getConversations(archived: Boolean, isPrivate: Boolean): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE threadId = :threadId")
    suspend fun getConversationByThreadId(threadId: Long): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE address = :address LIMIT 1")
    suspend fun getConversationByAddress(address: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(conversations: List<ConversationEntity>)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE threadId = :threadId")
    suspend fun deleteConversation(threadId: Long)

    @Query("UPDATE conversations SET snippet = :snippet, date = :date, read = :read, messageCount = messageCount + 1 WHERE threadId = :threadId")
    suspend fun updateLastMessage(threadId: Long, snippet: String, date: Long, read: Boolean)

    @Query("UPDATE conversations SET read = :read WHERE threadId = :threadId")
    suspend fun updateReadStatus(threadId: Long, read: Boolean)
}
