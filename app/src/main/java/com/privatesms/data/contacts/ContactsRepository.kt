package com.privatesms.data.contacts

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.privatesms.domain.model.Contact
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private class ContactWrapper(val contact: Contact?)
    private val contactCache = java.util.concurrent.ConcurrentHashMap<String, ContactWrapper>()

    suspend fun getContactByAddress(address: String): Contact? = withContext(Dispatchers.IO) {
        if (address.isEmpty()) return@withContext null
        contactCache[address]?.let { return@withContext it.contact }
        
        var resolvedContact: Contact? = null
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(address)
            )
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_URI,
                    ContactsContract.PhoneLookup.NUMBER
                ),
                null,
                null,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    val photoIdx = it.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                    val numIdx = it.getColumnIndex(ContactsContract.PhoneLookup.NUMBER)
                    
                    val name = if (nameIdx != -1) it.getString(nameIdx) ?: "" else ""
                    val photoUri = if (photoIdx != -1) it.getString(photoIdx) else null
                    val number = if (numIdx != -1) it.getString(numIdx) ?: address else address
                    
                    resolvedContact = Contact(
                        name = name,
                        phoneNumber = number,
                        photoUri = photoUri
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        contactCache[address] = ContactWrapper(resolvedContact)
        return@withContext resolvedContact
    }

    suspend fun getAllContacts(): List<Contact> = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<Contact>()
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )

            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                while (it.moveToNext()) {
                    val name = if (nameIdx != -1) it.getString(nameIdx) ?: "" else ""
                    val number = if (numIdx != -1) it.getString(numIdx) ?: "" else ""
                    val photoUri = if (photoIdx != -1) it.getString(photoIdx) else null

                    if (number.isNotEmpty() && contacts.none { c -> c.phoneNumber == number }) {
                        contacts.add(Contact(name = name, phoneNumber = number, photoUri = photoUri))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext contacts
    }
}
