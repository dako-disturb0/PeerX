package com.example.data.database

import androidx.room.*
import com.example.data.model.Contact
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY lastSeen DESC")
    fun getAllContacts(): Flow<List<Contact>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact)

    @Update
    suspend fun updateContact(contact: Contact)

    @Query("DELETE FROM contacts WHERE hash = :hash")
    suspend fun deleteContactByHash(hash: String)

    @Query("SELECT * FROM contacts WHERE hash = :hash LIMIT 1")
    suspend fun getContactByHash(hash: String): Contact?
}
