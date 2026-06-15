package com.privatesms.data.db

import androidx.room.*
import com.privatesms.data.model.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY date ASC")
    fun getMessagesForThread(threadId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageById(id: Long): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    @Query("DELETE FROM messages WHERE threadId = :threadId")
    suspend fun deleteMessagesForThread(threadId: Long)

    @Query("SELECT * FROM messages WHERE isScheduled = 1 ORDER BY scheduledTime ASC")
    fun getScheduledMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE isScheduled = 1 AND scheduledTime <= :currentTime")
    suspend fun getPendingScheduledMessagesSync(currentTime: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE body LIKE '%' || :query || '%' ORDER BY date DESC")
    fun searchMessages(query: String): Flow<List<MessageEntity>>
}
