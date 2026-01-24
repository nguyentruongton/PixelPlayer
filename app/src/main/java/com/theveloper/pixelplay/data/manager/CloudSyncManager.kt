package com.theveloper.pixelplay.data.manager

import com.theveloper.pixelplay.data.database.CloudSongDao
import com.theveloper.pixelplay.data.model.CloudSong
import com.theveloper.pixelplay.data.repository.TelegramRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class CloudSyncManager @Inject constructor(
    private val telegramRepository: TelegramRepository,
    private val cloudSongDao: CloudSongDao
) {
    
    val savedSongs: Flow<List<CloudSong>> = cloudSongDao.getAllCloudSongs()

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
                    artist = audio.performer,
                    duration = audio.duration,
                    size = audio.audio.size,
                    dateAdded = message.date.toLong()
                )
            }
            
            // 4. Save to DB (Replace logic)
            // Ideally we should merge, but for MVP replace is safer to keep sync simple
            if (cloudSongs.isNotEmpty()) {
                cloudSongDao.clearAll() // Optional: Clear old cache if we want full fresh list
                cloudSongDao.insertAll(cloudSongs)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
