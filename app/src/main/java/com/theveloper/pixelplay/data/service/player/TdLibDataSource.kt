package com.theveloper.pixelplay.data.service.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.theveloper.pixelplay.data.repository.TelegramRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.RandomAccessFile

class TdLibDataSource(
    private val telegramRepository: TelegramRepository
) : BaseDataSource(/* isNetwork = */ true) {

    private var fileId: Int = 0
    private var randomAccessFile: RandomAccessFile? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0
    private var isOpen = false
    private var currentPosition: Long = 0

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        this.uri = dataSpec.uri
        
        // Expected URI format: tdlib://{fileId}
        // Assuming fileId is passed as authority or path
        try {
            val fileIdStr = dataSpec.uri.host ?: dataSpec.uri.path?.removePrefix("/")
            fileId = fileIdStr!!.toInt()
        } catch (e: Exception) {
            throw java.io.IOException("Invalid TDLib URI: ${dataSpec.uri}")
        }

        // Trigger download and get path (blocking for simplicity in this context, 
        // though DataSource.open() is called on background thread usually)
        val path = runBlocking {
            telegramRepository.downloadFile(fileId).first()
        }

        if (path.isEmpty()) {
             throw java.io.IOException("Could not get file path for ID $fileId")
        }

        val file = File(path)
        randomAccessFile = RandomAccessFile(file, "r")
        randomAccessFile?.seek(dataSpec.position)
        currentPosition = dataSpec.position
        isOpen = true

        // We assume unknown length for streaming if not provided, 
        // or we could query CloudSong from DB to get expected size.
        // For now returning C.LENGTH_UNSET as we are "streaming".
        val length = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
             C.LENGTH_UNSET.toLong()
        } else {
            dataSpec.length
        }
        bytesRemaining = length

        transferStarted(dataSpec)
        return length
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (!isOpen) return -1
        if (bytesRemaining == 0L) return -1

        val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            readLength
        } else {
            minOf(bytesRemaining, readLength.toLong()).toInt()
        }

        if (bytesToRead == 0) return 0

        // Streaming Loop
        // Try to read from file. If EOF but expecting more (streaming), wait.
        var bytesRead = 0
        var retries = 0
        val MAX_RETRIES = 50 // 5 seconds wait max per chunk request?
        
        while (bytesRead <= 0 && retries < MAX_RETRIES) {
             bytesRead = randomAccessFile?.read(buffer, offset, bytesToRead) ?: -1
             if (bytesRead < 0) {
                 // EOF reached. Check if we should wait.
                 // Ideally we check if "download complete". 
                 // For MVP, we sleep briefly hoping for more data.
                 // WARNING: blocking here.
                 Thread.sleep(100)
                 retries++
                 // If file grew, seek will work? RAF.read updates pointer automatically.
                 // We don't strictly know if download is finished here without extra state.
                 // Assume if after 5 seconds no data, it's done or stalled.
                 if (retries >= MAX_RETRIES) {
                     return -1 // Give up
                 }
             } else {
                 break // Got data
             }
        }

        if (bytesRead > 0) {
            currentPosition += bytesRead
            if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                bytesRemaining -= bytesRead
            }
            bytesTransferred(bytesRead)
        }

        return bytesRead
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        if (isOpen) {
            isOpen = false
            randomAccessFile?.close()
            randomAccessFile = null
            transferEnded()
        }
    }

    class Factory(
        private val telegramRepository: TelegramRepository
    ) : androidx.media3.datasource.DataSource.Factory {
        override fun createDataSource(): androidx.media3.datasource.DataSource {
            return TdLibDataSource(telegramRepository)
        }
    }
}
