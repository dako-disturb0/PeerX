package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey val hash: String,
    val name: String,
    val lastSeen: Long = System.currentTimeMillis(),
    val isBlocked: Boolean = false
)

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey val id: String,
    val peerHash: String,
    val fromMe: Boolean,
    val fromName: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSystem: Boolean = false,
    
    // For reply feature
    val replyToId: String? = null,
    val replyToName: String? = null,
    val replyToContent: String? = null
)
