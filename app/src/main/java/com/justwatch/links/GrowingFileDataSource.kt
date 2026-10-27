package com.justwatch.links

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException
import java.io.RandomAccessFile

/** Waits for the downloader at the live edge, including seeks beyond cached bytes. */
internal @OptIn(UnstableApi::class) class GrowingFileDataSource(
    private val download: GrowingDownload
) : BaseDataSource(false) {
    private var input: RandomAccessFile? = null
    private var position = 0L
    private var remaining = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        input = RandomAccessFile(download.file, "r")
        position = dataSpec.position
        input!!.seek(position)
        remaining =
            if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length
            else if (download.complete) (download.file.length() - position).coerceAtLeast(0)
            else C.LENGTH_UNSET.toLong()
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val available = download.availableAt(position)
        if (available < 0) return C.RESULT_END_OF_INPUT
        val count =
            minOf(length.toLong(), available, if (remaining < 0) Long.MAX_VALUE else remaining)
                .toInt()
        val result = input!!.read(buffer, offset, count)
        if (result < 0) throw IOException("Download file changed during playback")
        if (result > 0) {
            position += result
            if (remaining >= 0) remaining -= result
            bytesTransferred(result)
        }
        return result
    }

    override fun getUri(): Uri = Uri.fromFile(download.file)

    override fun close() {
        try {
            input?.close()
        } finally {
            input = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }
}
