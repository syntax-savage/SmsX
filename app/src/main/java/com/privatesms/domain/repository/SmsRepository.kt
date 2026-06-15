package com.privatesms.domain.repository

import com.privatesms.domain.model.Conversation
import com.privatesms.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface SmsRepository {
    fun getConversations(archived: Boolean, isPrivate: Boolean): Flow<List<Conversation>>
    fun getMessagesForThread(threadId: Long): Flow<List<Message>>
    suspend fun sendSms(address: String, body: String, subscriptionId: Int? = null)
    suspend fun deleteMessage(messageId: Long)
    suspend fun deleteConversation(threadId: Long)
    suspend fun archiveConversation(threadId: Long, archived: Boolean)
    suspend fun markConversationPrivate(threadId: Long, isPrivate: Boolean)
    fun searchMessages(query: String): Flow<List<Message>>
    suspend fun setReadStatus(threadId: Long, read: Boolean)
    fun getScheduledMessages(): Flow<List<Message>>
    suspend fun scheduleSms(address: String, body: String, scheduledTime: Long)
    suspend fun cancelScheduledSms(messageId: Long)
    suspend fun reschedulePendingAlarms()
}
