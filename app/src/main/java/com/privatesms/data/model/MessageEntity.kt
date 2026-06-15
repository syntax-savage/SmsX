package com.privatesms.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "messages",
    indices = [Index(value = ["threadId"])]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val threadId: Long,
    val address: String,
    val body: String,
    val type: Int, // 1 = received, 2 = sent
    val date: Long, // timestamp ms
    val dateSent: Long,
    val read: Boolean,
    val status: Int, // 0 = NONE, 1 = SENT, 2 = DELIVERED, 3 = FAILED
    val isScheduled: Boolean,
    val scheduledTime: Long? = null,
    val isEncrypted: Boolean = true,
    val attachmentPath: String? = null // local URI for MMS image
) {
    companion object {
        const val TYPE_RECEIVED = 1
        const val TYPE_SENT = 2

        const val STATUS_NONE = 0
        const val STATUS_SENT = 1
        const val STATUS_DELIVERED = 2
        const val STATUS_FAILED = 3
    }
}
