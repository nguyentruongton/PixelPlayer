package com.theveloper.pixelplay.data.manager

import com.theveloper.pixelplay.data.database.CloudSongDao
import com.theveloper.pixelplay.data.model.CloudAlbum
import com.theveloper.pixelplay.data.model.CloudArtist
import com.theveloper.pixelplay.data.model.CloudSong
import com.theveloper.pixelplay.data.repository.TelegramRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.drinkless.tdlib.TdApi
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncManager @Inject constructor(
    private val telegramRepository: TelegramRepository,
    private val cloudSongDao: CloudSongDao,
    private val metadataExtractor: CloudMetadataExtractor
) {
    
    val savedSongs: Flow<List<CloudSong>> = cloudSongDao.getAllCloudSongs()
    
    val pendingMetadataCount: Flow<Int> = cloudSongDao.getPendingMetadataCount()

    suspend fun syncCloudSongs(): Result<Unit> {
        return try {
            // 1. Get Chat ID
            val chatId = telegramRepository.getPixelPlayCloudChat().firstOrNull()
                ?: return Result.failure(Exception("Cloud chat not found or created"))
            
            // 2. Fetch Messages
            val messages = telegramRepository.getAudioMessages(chatId).first()
            
            // 3. Map to CloudSong entities
            val cloudSongs = messages.mapNotNull { message ->
                val content = message.content as? TdApi.MessageAudio ?: return@mapNotNull null
                val audio = content.audio
                
                CloudSong(
                    id = message.id,
                    fileId = audio.audio.id,
                    remoteFileId = audio.audio.remote.id,
                    title = audio.title.ifEmpty { audio.fileName },
                    artist = audio.performer.ifBlank { "Unknown Artist" },
                    duration = audio.duration,
                    size = audio.audio.size,
                    dateAdded = message.date.toLong()
                )
            }
            
            // 4. Save to DB (Replace logic)
            if (cloudSongs.isNotEmpty()) {
                cloudSongDao.clearAll()
                cloudSongDao.insertAll(cloudSongs)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "CloudSyncManager: Failed to sync cloud songs")
            Result.failure(e)
        }
    }
    
    /**
     * Extracts metadata for songs that haven't been processed yet.
     * Called in background, processes in batches to avoid overwhelming network.
     */
    suspend fun extractPendingMetadata(batchSize: Int = 5): Int {
        val pending = cloudSongDao.getSongsNeedingMetadata(batchSize)
        Timber.d("CloudSyncManager: Extracting metadata for ${pending.size} songs")
        
        var extracted = 0
        pending.forEach { song ->
            val updated = metadataExtractor.extractMetadata(song)
            cloudSongDao.updateSong(updated)
            extracted++
        }
        
        return extracted
    }
    
    /**
     * Gets all albums with their cover artwork from the latest song in each album.
     */
    fun getAlbumsWithArtwork(): Flow<List<CloudAlbum>> {
        return cloudSongDao.getAllAlbums().map { albumNames ->
            albumNames.mapNotNull { albumName ->
                val songs = cloudSongDao.getSongsByAlbum(albumName).first()
                if (songs.isEmpty()) return@mapNotNull null
                
                val latestSong = cloudSongDao.getLatestSongInAlbum(albumName)
                
                CloudAlbum(
                    name = albumName,
                    artist = latestSong?.albumArtist ?: latestSong?.artist,
                    artworkPath = latestSong?.artworkPath,
                    songCount = songs.size,
                    songs = songs
                )
            }
        }
    }
    
    /**
     * Gets all artists with their cover artwork from the latest song by each artist.
     */
    fun getArtistsWithArtwork(): Flow<List<CloudArtist>> {
        return cloudSongDao.getAllArtists().map { artistNames ->
            artistNames.mapNotNull { artistName ->
                val songs = cloudSongDao.getSongsByArtist(artistName).first()
                if (songs.isEmpty()) return@mapNotNull null
                
                val latestSong = cloudSongDao.getLatestSongByArtist(artistName)
                
                CloudArtist(
                    name = artistName,
                    artworkPath = latestSong?.artworkPath,
                    songCount = songs.size,
                    albumCount = songs.map { it.album }.distinct().size
                )
            }
        }
    }
    
    /**
     * Clears all cloud song cache and artwork files.
     */
    suspend fun clearCloudCache() {
        Timber.d("CloudSyncManager: Clearing cloud cache")
        cloudSongDao.clearAll()
        metadataExtractor.clearArtworkCache()
    }
}
