package com.theveloper.pixelplay.presentation.viewmodel

import app.cash.turbine.test
import com.theveloper.pixelplay.data.repository.TelegramRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class TelegramLoginViewModelTest {

    private lateinit var viewModel: TelegramLoginViewModel
    private val mockRepository: TelegramRepository = mockk()
    private val testDispatcher = StandardTestDispatcher()
    private val authStateFlow = MutableStateFlow<TdApi.AuthorizationState?>(null)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockRepository.getAuthorizationState() } returns authStateFlow
        viewModel = TelegramLoginViewModel(mockRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("Initial State")
    inner class InitialStateTests {

        @Test
        fun `initial state has PHONE step`() = runTest(testDispatcher) {
            assertThat(viewModel.uiState.value.step).isEqualTo(TelegramLoginViewModel.LoginStep.PHONE)
        }

        @Test
        fun `initial state is not loading`() = runTest(testDispatcher) {
            assertThat(viewModel.uiState.value.isLoading).isFalse()
        }

        @Test
        fun `initial state has no error`() = runTest(testDispatcher) {
            assertThat(viewModel.uiState.value.error).isNull()
        }
    }

    @Nested
    @DisplayName("sendPhoneNumber")
    inner class SendPhoneNumberTests {

        @Test
        fun `sendPhoneNumber sets loading state`() = runTest(testDispatcher) {
            coEvery { mockRepository.sendPhoneNumber(any()) } returns flowOf(Result.success(Unit))

            viewModel.onPhoneNumberChanged("+84123456789")
            
            viewModel.uiState.test {
                skipItems(1) // Skip initial state
                
                viewModel.sendPhoneNumber()
                
                val loadingState = awaitItem()
                assertThat(loadingState.isLoading).isTrue()
                
                cancelAndConsumeRemainingEvents()
            }
        }

        @Test
        fun `sendPhoneNumber success moves to CODE step`() = runTest(testDispatcher) {
            coEvery { mockRepository.sendPhoneNumber(any()) } returns flowOf(Result.success(Unit))

            viewModel.onPhoneNumberChanged("+84123456789")
            viewModel.sendPhoneNumber()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.step).isEqualTo(TelegramLoginViewModel.LoginStep.CODE)
        }

        @Test
        fun `sendPhoneNumber failure shows error`() = runTest(testDispatcher) {
            coEvery { mockRepository.sendPhoneNumber(any()) } returns flowOf(Result.failure(Exception("Invalid phone")))

            viewModel.onPhoneNumberChanged("invalid")
            viewModel.sendPhoneNumber()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.error).contains("Invalid phone")
        }

        @Test
        fun `sendPhoneNumber with blank phone does nothing`() = runTest(testDispatcher) {
            viewModel.sendPhoneNumber()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.step).isEqualTo(TelegramLoginViewModel.LoginStep.PHONE)
            assertThat(viewModel.uiState.value.isLoading).isFalse()
        }
    }

    @Nested
    @DisplayName("checkCode")
    inner class CheckCodeTests {

        @Test
        fun `checkCode success moves to LOGGED_IN step`() = runTest(testDispatcher) {
            coEvery { mockRepository.checkAuthenticationCode(any()) } returns flowOf(Result.success(Unit))

            viewModel.onCodeChanged("12345")
            viewModel.checkCode()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.step).isEqualTo(TelegramLoginViewModel.LoginStep.LOGGED_IN)
        }

        @Test
        fun `checkCode password required moves to PASSWORD step`() = runTest(testDispatcher) {
            coEvery { mockRepository.checkAuthenticationCode(any()) } returns flowOf(
                Result.failure(Exception("PASSWORD_REQUIRED"))
            )

            viewModel.onCodeChanged("12345")
            viewModel.checkCode()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.step).isEqualTo(TelegramLoginViewModel.LoginStep.PASSWORD)
        }

        @Test
        fun `checkCode failure shows error`() = runTest(testDispatcher) {
            coEvery { mockRepository.checkAuthenticationCode(any()) } returns flowOf(
                Result.failure(Exception("Invalid code"))
            )

            viewModel.onCodeChanged("wrong")
            viewModel.checkCode()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.error).contains("Invalid code")
        }
    }

    @Nested
    @DisplayName("checkPassword")
    inner class CheckPasswordTests {

        @Test
        fun `checkPassword success moves to LOGGED_IN step`() = runTest(testDispatcher) {
            coEvery { mockRepository.checkAuthenticationPassword(any()) } returns flowOf(Result.success(Unit))

            viewModel.onPasswordChanged("password123")
            viewModel.checkPassword()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.step).isEqualTo(TelegramLoginViewModel.LoginStep.LOGGED_IN)
        }

        @Test
        fun `checkPassword failure shows error`() = runTest(testDispatcher) {
            coEvery { mockRepository.checkAuthenticationPassword(any()) } returns flowOf(
                Result.failure(Exception("Wrong password"))
            )

            viewModel.onPasswordChanged("wrong")
            viewModel.checkPassword()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.error).contains("Wrong password")
        }
    }

    @Nested
    @DisplayName("Authorization State Observation")
    inner class AuthorizationStateTests {

        @Test
        fun `observes AuthorizationStateWaitPhoneNumber`() = runTest(testDispatcher) {
            authStateFlow.value = TdApi.AuthorizationStateWaitPhoneNumber()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.step).isEqualTo(TelegramLoginViewModel.LoginStep.PHONE)
        }

        @Test
        fun `observes AuthorizationStateWaitCode`() = runTest(testDispatcher) {
            authStateFlow.value = TdApi.AuthorizationStateWaitCode()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.step).isEqualTo(TelegramLoginViewModel.LoginStep.CODE)
        }

        @Test
        fun `observes AuthorizationStateWaitPassword`() = runTest(testDispatcher) {
            authStateFlow.value = TdApi.AuthorizationStateWaitPassword()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.step).isEqualTo(TelegramLoginViewModel.LoginStep.PASSWORD)
        }

        @Test
        fun `observes AuthorizationStateReady`() = runTest(testDispatcher) {
            authStateFlow.value = TdApi.AuthorizationStateReady()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.step).isEqualTo(TelegramLoginViewModel.LoginStep.LOGGED_IN)
        }
    }

    @Nested
    @DisplayName("Input Changes")
    inner class InputChangesTests {

        @Test
        fun `onPhoneNumberChanged updates state`() = runTest(testDispatcher) {
            viewModel.onPhoneNumberChanged("+84123456789")
            
            assertThat(viewModel.uiState.value.phoneNumber).isEqualTo("+84123456789")
        }

        @Test
        fun `onCodeChanged updates state`() = runTest(testDispatcher) {
            viewModel.onCodeChanged("12345")
            
            assertThat(viewModel.uiState.value.code).isEqualTo("12345")
        }

        @Test
        fun `onPasswordChanged updates state`() = runTest(testDispatcher) {
            viewModel.onPasswordChanged("secret")
            
            assertThat(viewModel.uiState.value.password).isEqualTo("secret")
        }

        @Test
        fun `input change clears error`() = runTest(testDispatcher) {
            // Simulate an error state
            coEvery { mockRepository.sendPhoneNumber(any()) } returns flowOf(
                Result.failure(Exception("Error"))
            )
            viewModel.onPhoneNumberChanged("invalid")
            viewModel.sendPhoneNumber()
            advanceUntilIdle()
            
            assertThat(viewModel.uiState.value.error).isNotNull()
            
            // Change input clears error
            viewModel.onPhoneNumberChanged("+84123456789")
            
            assertThat(viewModel.uiState.value.error).isNull()
        }
    }
}
