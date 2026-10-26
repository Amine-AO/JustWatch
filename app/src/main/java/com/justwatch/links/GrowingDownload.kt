package com.justwatch.links

import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile

/** One sequential download. An empty read while it is active is not EOF. */
class GrowingDownload(val file: File) {
    private val monitor = Object()
    @Volatile var complete = false
        private set
    @Volatile private var failure: IOException? = null

    fun finish(error: IOException? = null) = synchronized(monitor) {
        if (!complete) {
            failure = error
            complete = true
            monitor.notifyAll()
        }
    }
    fun cancel() = synchronized(monitor) {
        failure = InterruptedIOException("Download cancelled")
        complete = true
        monitor.notifyAll()
    }
    fun signal() = synchronized(monitor) { monitor.notifyAll() }

    @Throws(IOException::class)
    fun availableAt(position: Long): Long = synchronized(monitor) {
        while (true) {
            failure?.let { throw it }
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
            val available = file.length() - position
            if (available > 0) return@synchronized available
            if (complete) return@synchronized -1L
            try { monitor.wait(100) } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException()
            }
        }
        @Suppress("UNREACHABLE_CODE") -1L
    }

    /** Accept only a complete front-loaded moov followed by some media bytes. */
    fun canStart(): Boolean = runCatching {
        if (!file.isFile) return@runCatching false
        RandomAccessFile(file, "r").use { input ->
            val available = input.length()
            var position = 0L
            var moov = false
            while (position <= minOf(available - 8, 16L * 1024 * 1024)) {
                input.seek(position)
                var size = input.readInt().toLong() and 0xffffffffL
                val type = input.readInt()
                var header = 8L
                if (size == 1L) {
                    if (available - position < 16) return@use false
                    size = input.readLong(); header = 16
                }
                if (type == 0x6d646174) return@use moov && available - position - header >= 64 * 1024
                if (size < header || size > available - position) return@use false
                if (type == 0x6d6f6f76) moov = true
                position += size
            }
            false
        }
    }.getOrDefault(false)
}
