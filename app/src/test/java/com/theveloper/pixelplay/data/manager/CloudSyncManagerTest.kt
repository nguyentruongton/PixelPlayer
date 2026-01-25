package com.theveloper.pixelplay.data.manager

import com.theveloper.pixelplay.data.database.CloudSongDao
import com.theveloper.pixelplay.data.model.CloudSong
import com.theveloper.pixelplay.data.repository.TelegramRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import com.google.common.truth.Truth.assertThat

@ExperimentalCoroutinesApi
class CloudSyncManagerTest {

    private lateinit var syncManager: CloudSyncManager
    private val mockRepository: TelegramRepository = mockk()
    private val mockCloudSongDao: CloudSongDao = mockk(relaxed = true)
    private val mockMetadataExtractor: CloudMetadataExtractor = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        syncManager = CloudSyncManager(mockRepository, mockCloudSongDao, mockMetadataExtractor)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("syncCloudSongs")
    inner class SyncCloudSongsTests {

        @Test
        fun `syncCloudSongs success saves songs to database`() = runTest(testDispatcher) {
            // Arrange
            val chatId = 100L
            val audioContent = TdApi.MessageAudio().apply {
                audio = TdApi.Audio().apply {
                    title = "Test Song"
                    performer = "Test Artist"
                    duration = 180
                    audio = TdApi.File().apply {
                        id = 1
                        size = 1024L
                        remote = TdApi.RemoteFile().apply {
                            id = "remote_id_1"
                        }
                    }
                }
            }
            val audioMessage = TdApi.Message().apply {
                id = 1L
                content = audioContent
                date = 1234567890
            }

            coEvery { mockRepository.getPixelPlayCloudChat() } returns flowOf(chatId)
            coEvery { mockRepository.getAudioMessages(chatId) } returns flowOf(listOf(audioMessage))
            coEvery { mockCloudSongDao.clearAll() } just Runs
            coEvery { mockCloudSongDao.insertAll(any()) } just Runs

            // Act
            val result = syncManager.syncCloudSongs()

            // Assert
            assertThat(result.isSuccess).isTrue()
            
            val capturedSongs = slot<List<CloudSong>>()
            coVerify { mockCloudSongDao.insertAll(capture(capturedSongs)) }
            
            assertThat(capturedSongs.captured).hasSize(1)
            assertThat(capturedSongs.captured[0].title).isEqualTo("Test Song")
            assertThat(capturedSongs.captured[0].artist).isEqualTo("Test Artist")
        }

        @Test
        fun `syncCloudSongs returns failure when chat not found`() = runTest(testDispatcher) {
            // Arrange
            coEvery { mockRepository.getPixelPlayCloudChat() } returns flowOf(null)

            // Act
            val result = syncManager.syncCloudSongs()

            // Assert
            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).contains("Cloud chat not found")
        }

        @Test
        fun `syncCloudSongs handles empty message list`() = runTest(testDispatcher) {
            // Arrange
            val chatId = 100L
            coEvery { mockRepository.getPixelPlayCloudChat() } returns flowOf(chatId)
            coEvery { mockRepository.getAudioMessages(chatId) } returns flowOf(emptyList())

            // Act
            val result = syncManager.syncCloudSongs()

            // Assert
            assertThat(result.isSuccess).isTrue()
            coVerify(exactly = 0) { mockCloudSongDao.insertAll(any()) }
        }

        @Test
        fun `syncCloudSongs clears old cache before inserting`() = runTest(testDispatcher) {
            // Arrange
            val chatId = 100L
            val audioContent = TdApi.MessageAudio().apply {
                audio = TdApi.Audio().apply {
                    title = "Song"
                    performer = "Artist"
                    duration = 120
                    audio = TdApi.File().apply {
                        id = 1
                        size = 512L
                        remote = TdApi.RemoteFile().apply { id = "remote_1" }
                    }
                }
            }
            val message = TdApi.Message().apply {
                id = 1L
                content = audioContent
                date = 1234567890
            }

            coEvery { mockRepository.getPixelPlayCloudChat() } returns flowOf(chatId)
            coEvery { mockRepository.getAudioMessages(chatId) } returns flowOf(listOf(message))
            coEvery { mockCloudSongDao.clearAll() } just Runs
            coEvery { mockCloudSongDao.insertAll(any()) } just Runs

            // Act
            syncManager.syncCloudSongs()

            // Assert
            coVerify(ordering = io.mockk.Ordering.ORDERED) {
                mockCloudSongDao.clearAll()
                mockCloudSongDao.insertAll(any())
            }
        }

        @Test
        fun `syncCloudSongs handles exception gracefully`() = runTest(testDispatcher) {
            // Arrange
            coEvery { mockRepository.getPixelPlayCloudChat() } throws RuntimeException("Network error")

            // Act
            val result = syncManager.syncCloudSongs()

            // Assert
            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).contains("Network error")
        }
    }


    @Nested
    @DisplayName("clearCloudCache")
    inner class ClearCloudCacheTests {
        
        @Test
        fun `clearCloudCache clears dao and artwork cache`() = runTest(testDispatcher) {
            // Arrange
            coEvery { mockCloudSongDao.clearAll() } just Runs
            coEvery { mockMetadataExtractor.clearArtworkCache() } just Runs
            
            // Act
            syncManager.clearCloudCache()
            
            // Assert
            coVerify(ordering = io.mockk.Ordering.ORDERED) {
                mockCloudSongDao.clearAll()
                mockMetadataExtractor.clearArtworkCache()
            }
        }
    }
}
