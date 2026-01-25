package com.theveloper.pixelplay.data.manager

import android.content.Context
import android.media.MediaMetadataRetriever
import com.theveloper.pixelplay.data.model.CloudSong
import com.theveloper.pixelplay.data.repository.TelegramRepository
import com.theveloper.pixelplay.utils.MediaMetadataRetrieverPool
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts full metadata (album, artwork, genre, year, trackNumber) from cloud song files.
 * Downloads the file from Telegram and uses MediaMetadataRetriever to parse ID3 tags.
 */
@Singleton
class CloudMetadataExtractor @Inject constructor(
    private val telegramRepository: TelegramRepository,
    @ApplicationContext private val context: Context
) {
    
    private val artworkCacheDir: File by lazy {
        File(context.cacheDir, "cloud_artwork").also { it.mkdirs() }
    }
    
    /**
     * Downloads file and extracts full metadata using MediaMetadataRetriever.
     * Saves embedded artwork to cache directory.
     * 
     * @param song The CloudSong to extract metadata for
     * @return Updated CloudSong with extracted metadata, or original with metadataExtracted=true if extraction fails
     */
    suspend fun extractMetadata(song: CloudSong): CloudSong = withContext(Dispatchers.IO) {
        try {
            Timber.d("CloudMetadataExtractor: Starting extraction for song ${song.id} - ${song.title}")
            
            // 1. Download file from Telegram
            val filePath = telegramRepository.downloadFileByRemoteId(song.remoteFileId).first()
            if (filePath.isEmpty()) {
                Timber.w("CloudMetadataExtractor: Failed to download file for song ${song.id}")
                return@withContext song.copy(metadataExtracted = true)
            }
            
            Timber.d("CloudMetadataExtractor: Downloaded file to $filePath")
            
            // 2. Extract metadata using pooled MediaMetadataRetriever
            val metadata = MediaMetadataRetrieverPool.withRetriever { retriever ->
                retriever.setDataSource(filePath)
                extractFromRetriever(retriever, song)
            } ?: song.copy(metadataExtracted = true)
            
            Timber.d("CloudMetadataExtractor: Extracted metadata - album=${metadata.album}, artist=${metadata.artist}")
            
            metadata
        } catch (e: Exception) {
            Timber.e(e, "CloudMetadataExtractor: Error extracting metadata for song ${song.id}")
            song.copy(metadataExtracted = true) // Mark as extracted to avoid retry loop
        }
    }
    
    private fun extractFromRetriever(
        retriever: MediaMetadataRetriever,
        song: CloudSong
    ): CloudSong {
        // Extract text metadata
        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() } ?: song.title
        
        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?.takeIf { it.isNotBlank() } ?: song.artist.ifBlank { "Unknown Artist" }
        
        val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            ?.takeIf { it.isNotBlank() } ?: "Unknown Album"
        
        val albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            ?.takeIf { it.isNotBlank() }
        
        val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            ?.takeIf { it.isNotBlank() }
        
        val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
            ?.toIntOrNull()
        
        val trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            ?.let { parseTrackNumber(it) }
        
        // Extract embedded artwork
        val artworkPath = extractAndSaveArtwork(retriever, song.id)
        
        return song.copy(
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            genre = genre,
            year = year,
            trackNumber = trackNumber,
            artworkPath = artworkPath,
            metadataExtracted = true
        )
    }
    
    /**
     * Parses track number from various formats like "1", "1/12", "01"
     */
    private fun parseTrackNumber(trackString: String): Int? {
        return trackString.split("/").firstOrNull()?.trim()?.toIntOrNull()
    }
    
    /**
     * Extracts embedded artwork and saves to cache directory.
     * @return Path to saved artwork file, or null if no artwork found
     */
    private fun extractAndSaveArtwork(retriever: MediaMetadataRetriever, songId: Long): String? {
        return try {
            val artworkBytes = retriever.embeddedPicture ?: return null
            
            val artworkFile = File(artworkCacheDir, "${songId}.jpg")
            FileOutputStream(artworkFile).use { fos ->
                fos.write(artworkBytes)
            }
            
            Timber.d("CloudMetadataExtractor: Saved artwork to ${artworkFile.absolutePath}")
            artworkFile.absolutePath
        } catch (e: Exception) {
            Timber.e(e, "CloudMetadataExtractor: Failed to save artwork for song $songId")
            null
        }
    }
    
    /**
     * Clears all cached artwork files.
     */
    suspend fun clearArtworkCache() = withContext(Dispatchers.IO) {
        try {
            artworkCacheDir.listFiles()?.forEach { it.delete() }
            Timber.d("CloudMetadataExtractor: Cleared artwork cache")
        } catch (e: Exception) {
            Timber.e(e, "CloudMetadataExtractor: Failed to clear artwork cache")
        }
    }
}
