package com.privatesms.domain.model

data class Message(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val type: Int, // 1 = received, 2 = sent
    val date: Long,
    val dateSent: Long,
    val read: Boolean,
    val status: Int,
    val isScheduled: Boolean,
    val scheduledTime: Long? = null,
    val isEncrypted: Boolean = true,
    val attachmentPath: String? = null,
    val contactName: String? = null,
    val contactPhotoUri: String? = null
)
