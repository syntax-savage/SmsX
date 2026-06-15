package com.privatesms.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val threadId: Long,
    val address: String,
    val snippet: String,
    val date: Long,
    val read: Boolean,
    val archived: Boolean = false,
    val isPrivate: Boolean = false,
    val messageCount: Int = 0,
    val isSpam: Boolean = false
)
