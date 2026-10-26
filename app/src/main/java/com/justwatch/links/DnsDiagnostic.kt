package com.justwatch.links

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.Request

object DnsDiagnostic {
    private val targets = listOf("www.instagram.com", "www.tiktok.com", "www.facebook.com")
    private val client = PublicMediaResolver.http.newBuilder()
        .followRedirects(false)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun run(): String = runInterruptible(Dispatchers.IO) {
        targets.joinToString("\n") { host ->
            try {
                val addresses = client.dns.lookup(host)
                val request = Request.Builder().url("https://$host/").head().build()
                client.newCall(request).execute().use { response ->
                    "$host: DNS OK (${addresses.size} addresses), HTTPS ${response.code}"
                }
            } catch (error: InterruptedException) {
                throw error
            } catch (error: Exception) {
                "$host: ${error.javaClass.simpleName}: ${error.message?.take(100) ?: "unknown error"}"
            }
        }
    }
}
