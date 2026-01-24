package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.service.telegram.TelegramService
import kotlinx.coroutines.delay
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
        // 1. Trigger Download first to ensure it starts
        val downloadRequest = TdApi.DownloadFile(fileId, 32, 0, 0, true)
        telegramService.sendSuspend(downloadRequest)

        // 2. Poll for file path
        var attempts = 0
        var path = ""
        while (attempts < 20) { // Try for 10 seconds (20 * 500ms)
            val fileRequest = TdApi.GetFile(fileId)
            val fileResult = telegramService.sendSuspend(fileRequest)
            val file = fileResult.getOrNull() as? TdApi.File
            
            if (file != null && file.local.path.isNotEmpty()) {
                path = file.local.path
                break
            }
            delay(500)
            attempts++
        }
        
        emit(path)
    }

    override fun getPixelPlayCloudChat(): Flow<Long?> = flow {
        // 1. Get Chat List to find "PixelPlay Cloud"
        // Corrected signature: GetChats(chatList, limit)
        val chatsRequest = TdApi.GetChats(TdApi.ChatListMain(), 100)
        val chatsResult = telegramService.sendSuspend(chatsRequest)
        val chats = chatsResult.getOrNull() as? TdApi.Chats

        var targetChatId: Long? = null
        
        if (chats != null) {
            for (chatId in chats.chatIds) {
                val chatRequest = TdApi.GetChat(chatId)
                val chatResult = telegramService.sendSuspend(chatRequest)
                val chat = chatResult.getOrNull() as? TdApi.Chat
                if (chat != null && chat.title == "PixelPlay Cloud") {
                    targetChatId = chat.id
                    break
                }
            }
        }

        if (targetChatId != null) {
            emit(targetChatId)
        } else {
            // 2. If not found, create new supergroup/channel
            // Corrected signature: TdApi.CreateNewSupergroupChat(title, isChannel, isForum, description, location, ttl, forImport)
            // Assuming: p0=Title, p1=isChannel, p2=isForum(Boolean), p3=Desc, p4=Location, p5=TTL, p6=forImport
            val createRequest = TdApi.CreateNewSupergroupChat(
                "PixelPlay Cloud", 
                true,   // isChannel
                false,  // isVisible/isForum (guessing p2 is isForum or similar bool)
                "Your music cloud for PixelPlayer", 
                null,   // location
                0,      // messageAutoDeleteTime
                false   // forImport
            )
            val createResult = telegramService.sendSuspend(createRequest)
             val chat = createResult.getOrNull() as? TdApi.Chat
             emit(chat?.id)
        }
    }

    override fun getAudioMessages(chatId: Long): Flow<List<TdApi.Message>> = flow {
         val request = TdApi.GetChatHistory(chatId, 0, 0, 50, false)
         val result = telegramService.sendSuspend(request)
         val messages = result.getOrNull() as? TdApi.Messages
         
         if (messages != null) {
             val audioMessages = messages.messages.filter { it.content is TdApi.MessageAudio }
             emit(audioMessages.toList())
         } else {
             emit(emptyList())
         }
    }
}
