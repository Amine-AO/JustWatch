package com.justwatch.links

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GrowingDownloadTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun waitsAtLiveEdgeThenReadsAppendAndOnlyEndsAfterCompletion() {
        val file = temp.newFile().apply { writeBytes(byteArrayOf(1, 2)) }
        val session = GrowingDownload(file)
        val worker = Executors.newSingleThreadExecutor()
        try {
            val result = worker.submit<Long> { session.availableAt(2) }
            try {
                result.get(150, TimeUnit.MILLISECONDS)
                fail("Live edge must wait")
            } catch (_: TimeoutException) {}
            file.appendBytes(byteArrayOf(3, 4))
            session.signal()
            assertEquals(2L, result.get(2, TimeUnit.SECONDS))
            session.finish()
            assertEquals(-1L, session.availableAt(4))
        } finally {
            session.cancel()
            worker.shutdownNow()
        }
    }

    @Test
    fun cancellationAndFailureWakeReadersSeekingBeyondDownloadedBytes() {
        for (cancel in listOf(true, false)) {
            val session = GrowingDownload(temp.newFile())
            val worker = Executors.newSingleThreadExecutor()
            try {
                val result =
                    worker.submit<Boolean> {
                        try {
                            session.availableAt(1000)
                            false
                        } catch (_: IOException) {
                            true
                        }
                    }
                if (cancel) session.cancel() else session.finish(IOException("Download failed"))
                assertTrue(result.get(2, TimeUnit.SECONDS))
                session.finish() // A late success must not erase cancellation/failure.
                try {
                    session.availableAt(0)
                    fail("Failure must persist")
                } catch (_: IOException) {}
            } finally {
                session.cancel()
                worker.shutdownNow()
            }
        }
    }

    private fun atom(type: String, payload: ByteArray = byteArrayOf()): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use {
            it.writeInt(payload.size + 8)
            it.writeBytes(type)
            it.write(payload)
        }
        return out.toByteArray()
    }

    @Test
    fun requiresCompleteFrontMetadataAndMediaBeforeOfferingEarlyPlayback() {
        val file = temp.newFile()
        val session = GrowingDownload(file)
        val ftyp = atom("ftyp")
        val moov = atom("moov", ByteArray(20))
        val mdat = atom("mdat", ByteArray(128 * 1024))
        file.writeBytes(ftyp + moov.copyOf(12))
        assertFalse(session.canStart())
        file.writeBytes(ftyp + moov + mdat.copyOf(1024))
        assertFalse(session.canStart())
        file.writeBytes(ftyp + moov + mdat.copyOf(80 * 1024))
        assertTrue(session.canStart())
        file.writeBytes(ftyp + mdat + moov)
        assertFalse("Tail metadata requires the full download fallback", session.canStart())
        file.writeBytes(
            byteArrayOf(0, 0, 0, 1, 109, 111, 111, 118, 127, 127, 127, 127, 127, 127, 127, 127)
        )
        assertFalse(session.canStart())
    }
}
