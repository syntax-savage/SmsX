package com.privatesms.domain.model

data class Conversation(
    val threadId: Long,
    val address: String,
    val snippet: String,
    val date: Long,
    val read: Boolean,
    val archived: Boolean = false,
    val isPrivate: Boolean = false,
    val messageCount: Int = 0,
    val isSpam: Boolean = false,
    val contactName: String? = null,
    val contactPhotoUri: String? = null
)
