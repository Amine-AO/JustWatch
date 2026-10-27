package com.justwatch.links

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** A staged engine is either committed after a smoke check or restored on failure/restart. */
internal class EngineStore(val target: File) {
    private val backup = File(target.parentFile, "yt-dlp.previous")
    val staged = File(target.parentFile, "yt-dlp.download")

    fun recover() {
        if (backup.exists()) {
            target.delete()
            check(backup.renameTo(target)) { "Could not restore the previous extractor" }
        }
        staged.delete()
    }

    fun install(checkEngine: () -> Unit) {
        check(staged.isFile && version(staged) != null) { "Invalid extractor archive" }
        check(!backup.exists()) { "Previous update needs recovery" }
        val hadPrevious = target.exists()
        if (hadPrevious) check(target.renameTo(backup)) { "Could not back up the extractor" }
        try {
            check(staged.renameTo(target)) { "Could not install the extractor" }
            checkEngine()
            check(!backup.exists() || backup.delete()) { "Could not finish the update" }
        } catch (error: Throwable) {
            target.delete()
            if (hadPrevious)
                check(backup.renameTo(target)) { "Could not restore the previous extractor" }
            throw error
        } finally {
            staged.delete()
        }
    }

    companion object {
        const val BUNDLED_VERSION = "2026.10.21"

        fun version(file: File): String? =
            runCatching<String?> {
                    file.inputStream().buffered().use { input ->
                        // Official yt-dlp is a Python zipapp with a shebang before its ZIP.
                        input.mark(128)
                        val prefix = ByteArray(128)
                        val count = input.read(prefix)
                        input.reset()
                        val offset =
                            (0 until count - 3).firstOrNull {
                                prefix[it] == 80.toByte() &&
                                    prefix[it + 1] == 75.toByte() &&
                                    prefix[it + 2] == 3.toByte() &&
                                    prefix[it + 3] == 4.toByte()
                            } ?: return null
                        repeat(offset) { input.read() }
                        ZipInputStream(input).use { zip ->
                            while (true) {
                                val entry = zip.nextEntry ?: return null
                                if (entry.name == "yt_dlp/version.py") {
                                    val source = zip.readBytes().toString(Charsets.UTF_8)
                                    return Regex(
                                            "__version__\\s*=\\s*['\"](\\d{4}\\.\\d{2}\\.\\d{2})['\"]"
                                        )
                                        .find(source)
                                        ?.groupValues
                                        ?.get(1)
                                }
                            }
                        }
                    }
                    null
                }
                .getOrNull()

        fun digest(file: File): String {
            val hash = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    hash.update(buffer, 0, count)
                }
            }
            return hash.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
