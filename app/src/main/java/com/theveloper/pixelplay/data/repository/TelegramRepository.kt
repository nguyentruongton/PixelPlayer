package com.theveloper.pixelplay.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.TdApi

interface TelegramRepository {
    fun sendPhoneNumber(phoneNumber: String): Flow<Result<Unit>>
    fun checkAuthenticationCode(code: String): Flow<Result<Unit>>
    fun checkAuthenticationPassword(password: String): Flow<Result<Unit>>
    fun isLoggedIn(): Flow<Boolean>
    fun getAuthorizationState(): StateFlow<TdApi.AuthorizationState?>
    fun logout(): Flow<Result<Unit>>
    fun downloadFile(fileId: Int): Flow<String>
}
