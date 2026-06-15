package com.privatesms.domain.model

data class Contact(
    val name: String,
    val phoneNumber: String,
    val photoUri: String? = null
)
