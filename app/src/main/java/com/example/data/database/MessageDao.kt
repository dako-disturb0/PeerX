package com.example.data.database

import androidx.room.*
import com.example.data.model.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE peerHash = :peerHash ORDER BY timestamp ASC")
    fun getMessagesForPeer(peerHash: String): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    @Query("DELETE FROM messages WHERE peerHash = :peerHash")
    suspend fun clearMessagesForPeer(peerHash: String)
}
