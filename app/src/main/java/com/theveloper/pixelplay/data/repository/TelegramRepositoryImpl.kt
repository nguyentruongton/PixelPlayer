package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.service.telegram.TelegramService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

class TelegramRepositoryImpl @Inject constructor(
    private val telegramService: TelegramService
) : TelegramRepository {

    override fun sendPhoneNumber(phoneNumber: String): Flow<Result<Unit>> = flow {
        val request = TdApi.SetAuthenticationPhoneNumber(phoneNumber, null)
        val result = telegramService.sendSuspend(request)
        if (result.isSuccess) emit(Result.success(Unit)) 
        else emit(Result.failure(Exception(result.exceptionOrNull())))
    }

    override fun checkAuthenticationCode(code: String): Flow<Result<Unit>> = flow {
         val request = TdApi.CheckAuthenticationCode(code)
         val result = telegramService.sendSuspend(request)
         if (result.isSuccess) emit(Result.success(Unit))
         else emit(Result.failure(Exception(result.exceptionOrNull())))
    }

    override fun checkAuthenticationPassword(password: String): Flow<Result<Unit>> = flow {
         val request = TdApi.CheckAuthenticationPassword(password)
         val result = telegramService.sendSuspend(request)
         if (result.isSuccess) emit(Result.success(Unit))
         else emit(Result.failure(Exception(result.exceptionOrNull())))
    }

    override fun isLoggedIn(): Flow<Boolean> = 
        telegramService.authorizationState.map { state ->
            state is TdApi.AuthorizationStateReady
        }
    
    override fun getAuthorizationState(): StateFlow<TdApi.AuthorizationState?> = 
        telegramService.authorizationState

    override fun logout(): Flow<Result<Unit>> = flow {
        val request = TdApi.LogOut()
        telegramService.sendSuspend(request)
        emit(Result.success(Unit))
    }

    override fun downloadFile(fileId: Int): Flow<String> = flow {
        // 1. Get File info to find local path
        val fileRequest = TdApi.GetFile(fileId)
        val fileResult = telegramService.sendSuspend(fileRequest)
        val file = fileResult.getOrNull() as? TdApi.File
        
        if (file != null) {
             val path = file.local.path
             emit(path) // Emit path immediately
             
             // 2. Start download
             val downloadRequest = TdApi.DownloadFile(fileId, 32, 0, 0, false)
             telegramService.sendSuspend(downloadRequest)
             // We don't wait for completion here, TdLibDataSource handles the reading
        } else {
            // Handle error
            emit("")
        }
    }
}
