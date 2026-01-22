package com.theveloper.pixelplay.data.service.telegram

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val client: Client
    
    private val _updates = kotlinx.coroutines.flow.MutableSharedFlow<TdApi.Object>(extraBufferCapacity = 100)
    val updates: Flow<TdApi.Object> = _updates.asSharedFlow()
    
    // Track authorization state for login persistence
    private val _authorizationState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authorizationState: StateFlow<TdApi.AuthorizationState?> = _authorizationState.asStateFlow()

    init {
        // Initialize TDLib client immediately
        val resultHandler = Client.ResultHandler { obj ->
            // Track authorization state changes
            if (obj is TdApi.UpdateAuthorizationState) {
                _authorizationState.value = obj.authorizationState
                android.util.Log.d("TelegramService", "Auth state changed: ${obj.authorizationState::class.simpleName}")
            }
            _updates.tryEmit(obj)
        }
        
        val exceptionHandler = Client.ExceptionHandler { e ->
            android.util.Log.e("TelegramService", "TDLib exception", e)
        }

        client = Client.create(resultHandler, exceptionHandler, exceptionHandler)
        
        // Set TdLib parameters on startup
        val dataDir = context.filesDir.absolutePath
        val parameters = TdApi.SetTdlibParameters().apply {
            useTestDc = false
            databaseDirectory = "$dataDir/tdlib"
            filesDirectory = "$dataDir/tdlib_files"
            useFileDatabase = true
            useChatInfoDatabase = true
            useMessageDatabase = true
            useSecretChats = false
            apiId = 13349681
            apiHash = "c0f7af77d301787239063a92d0429e3d"
            systemLanguageCode = "en"
            deviceModel = android.os.Build.MODEL
            systemVersion = android.os.Build.VERSION.RELEASE
            applicationVersion = "1.0"
        }
        client.send(parameters) { result ->
            if (result is TdApi.Error) {
                android.util.Log.e("TelegramService", "TdLib init error: ${result.message}")
            } else {
                android.util.Log.d("TelegramService", "TdLib initialized successfully")
            }
        }
    }

    fun send(function: TdApi.Function<*>, callback: (Result<TdApi.Object>) -> Unit) {
        client.send(function) { result ->
            if (result is TdApi.Error) {
                callback(Result.failure(Exception(result.message)))
            } else {
                callback(Result.success(result))
            }
        }
    }

    // Coroutine-friendly suspend function
    suspend fun sendSuspend(function: TdApi.Function<*>): Result<TdApi.Object> {
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            send(function) { result ->
                if (cont.isActive) {
                    cont.resumeWith(Result.success(result))
                }
            }
        }
    }
}
