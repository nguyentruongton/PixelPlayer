package com.theveloper.pixelplay.data.model

/**
 * Represents a cloud artist with aggregated data from cloud songs.
 */
data class CloudArtist(
    val name: String,
    val artworkPath: String?,
    val songCount: Int,
    val albumCount: Int
)
