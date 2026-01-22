package com.theveloper.pixelplay.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cloud_songs")
data class CloudSong(
    @PrimaryKey val id: Long, // Telegram Message ID or File ID? Using Message ID is safer for uniqueness in a chat
    val fileId: Int, // TDLib File ID (local ID)
    val remoteFileId: String, // Persistent Remote ID
    val title: String,
    val artist: String,
    val duration: Int,
    val size: Long,
    val dateAdded: Long
)
