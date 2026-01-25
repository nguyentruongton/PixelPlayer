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
    val album: String = "Unknown Album",
    val albumArtist: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val artworkPath: String? = null, // Local cached artwork path
    val metadataExtracted: Boolean = false, // Flag to mark if full metadata was extracted
    val duration: Int,
    val size: Long,
    val dateAdded: Long
)
