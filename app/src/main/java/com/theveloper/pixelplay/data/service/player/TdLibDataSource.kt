package com.theveloper.pixelplay.data.service.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
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
        try {
            val fileIdStr = dataSpec.uri.host ?: dataSpec.uri.path?.removePrefix("/")
            fileId = fileIdStr!!.toInt()
        } catch (e: Exception) {
            throw java.io.IOException("Invalid TDLib URI: ${dataSpec.uri}")
        }

        // Trigger download and get path (waits for path to be assigned by TDLib)
        val path = runBlocking {
            telegramRepository.downloadFile(fileId).first()
        }

        if (path.isEmpty()) {
             throw java.io.IOException("Could not get file path for ID $fileId")
        }

        val file = File(path)
        
        // Wait for file to actually exist on disk (download might be initializing)
        var existenceChecks = 0
        while (!file.exists() && existenceChecks < 20) { // Wait up to 2 seconds
            try { Thread.sleep(100) } catch (e: InterruptedException) { }
            existenceChecks++
        }

        if (!file.exists()) {
            throw java.io.IOException("File not found at path: $path")
        }

        randomAccessFile = RandomAccessFile(file, "r")
        randomAccessFile?.seek(dataSpec.position)
        currentPosition = dataSpec.position
        isOpen = true

        // For streaming, if length is unset, we return unset.
        // If we knew the total size from DB, we could return it, but Unset is safer for varying streams.
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

        var bytesRead = 0
        var retries = 0
        // Wait longer for streaming data (up to 30 seconds total if stalled)
        // 300 * 100ms = 30000ms = 30s
        val MAX_RETRIES = 300 
        
        while (bytesRead <= 0 && retries < MAX_RETRIES) {
             bytesRead = randomAccessFile?.read(buffer, offset, bytesToRead) ?: -1
             if (bytesRead < 0) {
                 // EOF reached. Wait for more data to be downloaded.
                 try { Thread.sleep(100) } catch (e: InterruptedException) { }
                 retries++
                 
                 // Reset bytesRead to 0 to loop again unless we really timed out
                 if (retries >= MAX_RETRIES) {
                     return -1 
                 }
                 bytesRead = 0 // Continue loop
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
