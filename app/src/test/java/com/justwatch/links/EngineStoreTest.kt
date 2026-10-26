package com.justwatch.links

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EngineStoreTest {
    @get:Rule val directory = TemporaryFolder()
    private fun archive(file: File, version: String) {
        ZipOutputStream(file.outputStream()).use {
            it.putNextEntry(ZipEntry("yt_dlp/version.py"))
            it.write("__version__ = '$version'\n".toByteArray())
            it.closeEntry()
        }
    }
    @Test fun failedRuntimeCheckRestoresWorkingEngine() {
        val store = EngineStore(File(directory.root, "yt-dlp"))
        archive(store.target, "2026.10.21")
        archive(store.staged, "2026.10.22")
        try { store.install { error("Python incompatible") }; fail("Must reject the update") }
        catch (_: IllegalStateException) { }
        assertEquals("2026.10.21", EngineStore.version(store.target))
        assertFalse(store.staged.exists())
    }
    @Test fun interruptedInstallRecoversOnRestart() {
        val store = EngineStore(File(directory.root, "yt-dlp"))
        archive(File(directory.root, "yt-dlp.previous"), "2026.10.21")
        archive(store.target, "2026.10.22")
        store.recover()
        assertEquals("2026.10.21", EngineStore.version(store.target))
    }
    @Test fun successfulInstallPersistsAcrossRestart() {
        val store = EngineStore(File(directory.root, "yt-dlp"))
        archive(store.target, "2026.10.21")
        archive(store.staged, "2026.10.22")
        store.install { assertEquals("2026.10.22", EngineStore.version(store.target)) }
        store.recover()
        assertEquals("2026.10.22", EngineStore.version(store.target))
    }
    @Test fun wildcardMatchesOnlySubdomainsAtLabelBoundary() {
        assertTrue(DomainMatch.matches("www.instagram.com", "*.instagram.com"))
        assertFalse(DomainMatch.matches("instagram.com", "*.instagram.com"))
        assertFalse(DomainMatch.matches("evilinstagram.com", "*.instagram.com"))
        assertFalse(DomainMatch.matches("instagram.com.evil.example", "*.instagram.com"))
    }
}
