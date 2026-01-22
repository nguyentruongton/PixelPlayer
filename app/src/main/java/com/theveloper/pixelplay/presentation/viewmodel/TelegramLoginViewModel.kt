package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.repository.TelegramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

@HiltViewModel
class TelegramLoginViewModel @Inject constructor(
    private val telegramRepository: TelegramRepository
) : ViewModel() {

    enum class LoginStep {
        PHONE, CODE, PASSWORD, LOGGED_IN
    }

    data class UiState(
        val step: LoginStep = LoginStep.PHONE,
        val isLoading: Boolean = false,
        val error: String? = null,
        val phoneNumber: String = "",
        val code: String = "",
        val password: String = ""
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        observeAuthorizationState()
    }

    /**
     * Observe TDLib authorization state and sync UI accordingly.
     * This ensures login state persists when navigating away and back.
     */
    private fun observeAuthorizationState() {
        viewModelScope.launch {
            telegramRepository.getAuthorizationState().collect { state ->
                val step = when (state) {
                    is TdApi.AuthorizationStateWaitPhoneNumber -> LoginStep.PHONE
                    is TdApi.AuthorizationStateWaitCode -> LoginStep.CODE
                    is TdApi.AuthorizationStateWaitPassword -> LoginStep.PASSWORD
                    is TdApi.AuthorizationStateReady -> LoginStep.LOGGED_IN
                    else -> null // Don't change step for other states (e.g., WaitTdlibParameters)
                }
                if (step != null) {
                    _uiState.update { it.copy(step = step, isLoading = false, error = null) }
                }
            }
        }
    }

    fun onPhoneNumberChanged(phone: String) {
        _uiState.update { it.copy(phoneNumber = phone, error = null) }
    }

    fun onCodeChanged(code: String) {
        _uiState.update { it.copy(code = code, error = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun sendPhoneNumber() {
        val phone = _uiState.value.phoneNumber
        if (phone.isBlank()) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            telegramRepository.sendPhoneNumber(phone).collect { result ->
                _uiState.update { it.copy(isLoading = false) }
                result.onSuccess {
                    _uiState.update { it.copy(step = LoginStep.CODE) }
                }.onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "Unknown error") }
                }
            }
        }
    }

    fun checkCode() {
        val code = _uiState.value.code
        if (code.isBlank()) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            telegramRepository.checkAuthenticationCode(code).collect { result ->
                _uiState.update { it.copy(isLoading = false) }
                result.onSuccess {
                    _uiState.update { it.copy(step = LoginStep.LOGGED_IN) }
                }.onFailure { e ->
                     // Check if error is PASSWORD_REQUIRED (needs checking actual TDLib error code usually)
                     // For simplicity, assuming if it implies password needed:
                     if (e.message?.contains("PASSWORD") == true) { // Very naive check
                         _uiState.update { it.copy(step = LoginStep.PASSWORD) }
                     } else {
                         _uiState.update { it.copy(error = e.message) }
                     }
                }
            }
        }
    }
    
    fun checkPassword() {
        val password = _uiState.value.password
        if (password.isBlank()) return
        
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            telegramRepository.checkAuthenticationPassword(password).collect { result ->
                _uiState.update { it.copy(isLoading = false) }
                 result.onSuccess {
                    _uiState.update { it.copy(step = LoginStep.LOGGED_IN) }
                }.onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
            }
        }
    }
}
