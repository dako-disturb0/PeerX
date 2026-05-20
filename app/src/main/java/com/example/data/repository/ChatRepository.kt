package com.example.data.repository

import com.example.data.database.ContactDao
import com.example.data.database.MessageDao
import com.example.data.model.Contact
import com.example.data.model.Message
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val contactDao: ContactDao,
    private val messageDao: MessageDao
) {
    val allContacts: Flow<List<Contact>> = contactDao.getAllContacts()

    fun getMessagesForPeer(peerHash: String): Flow<List<Message>> = messageDao.getMessagesForPeer(peerHash)

    suspend fun saveContact(username: String, hash: String, isBlocked: Boolean = false) {
        val contact = Contact(hash = hash, name = username, lastSeen = System.currentTimeMillis(), isBlocked = isBlocked)
        contactDao.insertContact(contact)
    }

    suspend fun deleteContact(hash: String) {
        contactDao.deleteContactByHash(hash)
        messageDao.clearMessagesForPeer(hash)
    }

    suspend fun blockContact(hash: String) {
        contactDao.getContactByHash(hash)?.let { contact ->
            contactDao.insertContact(contact.copy(isBlocked = !contact.isBlocked))
        }
    }

    suspend fun saveMessage(message: Message) {
        messageDao.insertMessage(message)
        // Also update contact's lastSeen
        contactDao.getContactByHash(message.peerHash)?.let { contact ->
            contactDao.insertContact(contact.copy(lastSeen = System.currentTimeMillis()))
        }
    }

    suspend fun clearMessages(peerHash: String) {
        messageDao.clearMessagesForPeer(peerHash)
    }
}
