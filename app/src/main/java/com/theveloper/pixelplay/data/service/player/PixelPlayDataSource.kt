package com.theveloper.pixelplay.data.service.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.theveloper.pixelplay.data.repository.TelegramRepository

class PixelPlayDataSource(
    context: Context,
    private val telegramRepository: TelegramRepository
) : DataSource {

    private val defaultDataSource = DefaultDataSource(context, true)
    private val tdLibDataSource = TdLibDataSource(telegramRepository)
    private var activeDataSource: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        defaultDataSource.addTransferListener(transferListener)
        tdLibDataSource.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val scheme = dataSpec.uri.scheme
        activeDataSource = if (scheme == "tdlib") {
            tdLibDataSource
        } else {
            defaultDataSource
        }
        return activeDataSource!!.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        return activeDataSource?.read(buffer, offset, readLength) ?: C.RESULT_END_OF_INPUT
    }

    override fun getUri(): Uri? {
        return activeDataSource?.uri
    }

    override fun close() {
        try {
            activeDataSource?.close()
        } finally {
            activeDataSource = null
        }
    }

    class Factory(
        private val context: Context,
        private val telegramRepository: TelegramRepository
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return PixelPlayDataSource(context, telegramRepository)
        }
    }
}
