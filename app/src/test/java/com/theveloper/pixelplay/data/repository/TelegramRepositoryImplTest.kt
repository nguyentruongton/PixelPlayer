package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.service.telegram.TelegramService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
class TelegramRepositoryImplTest {

    private lateinit var repository: TelegramRepositoryImpl
    private val mockTelegramService: TelegramService = mockk()
    private val testDispatcher = StandardTestDispatcher()
    private val authStateFlow = MutableStateFlow<TdApi.AuthorizationState?>(null)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockTelegramService.authorizationState } returns authStateFlow
        repository = TelegramRepositoryImpl(mockTelegramService)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("sendPhoneNumber")
    inner class SendPhoneNumberTests {

        @Test
        fun `sendPhoneNumber success returns Result success`() = runTest(testDispatcher) {
            coEvery { mockTelegramService.sendSuspend(any()) } returns Result.success(TdApi.Ok())

            val result = repository.sendPhoneNumber("+84123456789").first()

            assertThat(result.isSuccess).isTrue()
        }

        @Test
        fun `sendPhoneNumber failure returns Result failure`() = runTest(testDispatcher) {
            coEvery { mockTelegramService.sendSuspend(any()) } returns Result.failure(Exception("Invalid phone"))

            val result = repository.sendPhoneNumber("invalid").first()

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).contains("Invalid phone")
        }
    }

    @Nested
    @DisplayName("checkAuthenticationCode")
    inner class CheckAuthenticationCodeTests {

        @Test
        fun `checkAuthenticationCode success returns Result success`() = runTest(testDispatcher) {
            coEvery { mockTelegramService.sendSuspend(any()) } returns Result.success(TdApi.Ok())

            val result = repository.checkAuthenticationCode("12345").first()

            assertThat(result.isSuccess).isTrue()
        }

        @Test
        fun `checkAuthenticationCode failure returns Result failure`() = runTest(testDispatcher) {
            coEvery { mockTelegramService.sendSuspend(any()) } returns Result.failure(Exception("Invalid code"))

            val result = repository.checkAuthenticationCode("wrong").first()

            assertThat(result.isFailure).isTrue()
        }
    }

    @Nested
    @DisplayName("checkAuthenticationPassword")
    inner class CheckAuthenticationPasswordTests {

        @Test
        fun `checkAuthenticationPassword success returns Result success`() = runTest(testDispatcher) {
            coEvery { mockTelegramService.sendSuspend(any()) } returns Result.success(TdApi.Ok())

            val result = repository.checkAuthenticationPassword("password123").first()

            assertThat(result.isSuccess).isTrue()
        }
    }

    @Nested
    @DisplayName("isLoggedIn")
    inner class IsLoggedInTests {

        @Test
        fun `isLoggedIn returns true when AuthorizationStateReady`() = runTest(testDispatcher) {
            authStateFlow.value = TdApi.AuthorizationStateReady()

            val result = repository.isLoggedIn().first()

            assertThat(result).isTrue()
        }

        @Test
        fun `isLoggedIn returns false for other states`() = runTest(testDispatcher) {
            authStateFlow.value = TdApi.AuthorizationStateWaitPhoneNumber()

            val result = repository.isLoggedIn().first()

            assertThat(result).isFalse()
        }
    }

    @Nested
    @DisplayName("logout")
    inner class LogoutTests {

        @Test
        fun `logout returns success`() = runTest(testDispatcher) {
            coEvery { mockTelegramService.sendSuspend(any()) } returns Result.success(TdApi.Ok())

            val result = repository.logout().first()

            assertThat(result.isSuccess).isTrue()
        }
    }

    @Nested
    @DisplayName("downloadFile")
    inner class DownloadFileTests {

        @Test
        fun `downloadFile returns file path when download completes`() = runTest(testDispatcher) {
            val mockFile = TdApi.File().apply {
                local = TdApi.LocalFile().apply {
                    path = "/data/music/song.mp3"
                }
            }
            coEvery { mockTelegramService.sendSuspend(match { it is TdApi.DownloadFile }) } returns Result.success(mockFile)
            coEvery { mockTelegramService.sendSuspend(match { it is TdApi.GetFile }) } returns Result.success(mockFile)

            val result = repository.downloadFile(123).first()

            assertThat(result).isEqualTo("/data/music/song.mp3")
        }

        @Test
        fun `downloadFile returns empty string on timeout`() = runTest(testDispatcher) {
            val emptyFile = TdApi.File().apply {
                local = TdApi.LocalFile().apply {
                    path = ""
                }
            }
            coEvery { mockTelegramService.sendSuspend(any()) } returns Result.success(emptyFile)

            val result = repository.downloadFile(123).first()

            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("getPixelPlayCloudChat")
    inner class GetPixelPlayCloudChatTests {

        @Test
        fun `getPixelPlayCloudChat returns existing chat id`() = runTest(testDispatcher) {
            val mockChats = TdApi.Chats().apply {
                chatIds = longArrayOf(100L, 200L)
            }
            val pixelPlayChat = TdApi.Chat().apply {
                id = 100L
                title = "PixelPlay Cloud"
            }
            val otherChat = TdApi.Chat().apply {
                id = 200L
                title = "Other Chat"
            }

            coEvery { mockTelegramService.sendSuspend(match { it is TdApi.GetChats }) } returns Result.success(mockChats)
            coEvery { mockTelegramService.sendSuspend(match { it is TdApi.GetChat && it.chatId == 100L }) } returns Result.success(pixelPlayChat)
            coEvery { mockTelegramService.sendSuspend(match { it is TdApi.GetChat && it.chatId == 200L }) } returns Result.success(otherChat)

            val result = repository.getPixelPlayCloudChat().first()

            assertThat(result).isEqualTo(100L)
        }

        @Test
        fun `getPixelPlayCloudChat creates new chat when not found`() = runTest(testDispatcher) {
            val emptyChats = TdApi.Chats().apply {
                chatIds = longArrayOf()
            }
            val newChat = TdApi.Chat().apply {
                id = 999L
                title = "PixelPlay Cloud"
            }

            coEvery { mockTelegramService.sendSuspend(match { it is TdApi.GetChats }) } returns Result.success(emptyChats)
            coEvery { mockTelegramService.sendSuspend(match { it is TdApi.CreateNewSupergroupChat }) } returns Result.success(newChat)

            val result = repository.getPixelPlayCloudChat().first()

            assertThat(result).isEqualTo(999L)
        }
    }

    @Nested
    @DisplayName("getAudioMessages")
    inner class GetAudioMessagesTests {

        @Test
        fun `getAudioMessages returns filtered audio messages`() = runTest(testDispatcher) {
            val audioContent = TdApi.MessageAudio().apply {
                audio = TdApi.Audio()
            }
            val textContent = TdApi.MessageText()
            
            val audioMessage = TdApi.Message().apply {
                id = 1L
                content = audioContent
            }
            val textMessage = TdApi.Message().apply {
                id = 2L
                content = textContent
            }
            
            val mockMessages = TdApi.Messages().apply {
                messages = arrayOf(audioMessage, textMessage)
            }

            coEvery { mockTelegramService.sendSuspend(match { it is TdApi.GetChatHistory }) } returns Result.success(mockMessages)

            val result = repository.getAudioMessages(100L).first()

            assertThat(result).hasSize(1)
            assertThat(result[0].id).isEqualTo(1L)
        }

        @Test
        fun `getAudioMessages returns empty list when no messages`() = runTest(testDispatcher) {
            coEvery { mockTelegramService.sendSuspend(any()) } returns Result.failure(Exception("Error"))

            val result = repository.getAudioMessages(100L).first()

            assertThat(result).isEmpty()
        }
    }
}
