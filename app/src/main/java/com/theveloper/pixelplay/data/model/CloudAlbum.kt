package com.theveloper.pixelplay.data.model

/**
 * Represents a cloud album with aggregated data from cloud songs.
 */
data class CloudAlbum(
    val name: String,
    val artist: String?,
    val artworkPath: String?,
    val songCount: Int,
    val songs: List<CloudSong>
)
