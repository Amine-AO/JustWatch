package com.justwatch.links

import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Short-lived loopback CONNECT proxy. TLS stays end-to-end in yt-dlp; only
 * hostname resolution and TCP connection establishment happen in the app.
 */
internal class ExtractionProxy(private val connect: (String) -> Socket) : Closeable {
    private val server = ServerSocket(0, 16, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
    private val workers = Executors.newCachedThreadPool()
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    @Volatile private var closed = false
    val url = "http://127.0.0.1:" + server.localPort

    init {
        workers.execute {
            while (!closed) {
                try {
                    val client = server.accept()
                    sockets.add(client)
                    if (closed) { client.close(); break }
                    workers.execute { tunnel(client) }
                } catch (_: Exception) {
                    if (!closed) close()
                }
            }
        }
    }

    private fun tunnel(client: Socket) {
        var upstream: Socket? = null
        try {
            client.soTimeout = 20_000
            val input = client.getInputStream()
            val header = StringBuilder()
            while (!header.endsWith("\r\n\r\n")) {
                val next = input.read()
                if (next < 0 || header.length >= 8192) return
                header.append(next.toChar())
            }
            val request = header.lineSequence().first().trim().split(' ')
            val authority = request.getOrNull(1).orEmpty()
            val host = authority.removeSuffix(":443")
            if (request.size != 3 || request[0] != "CONNECT" ||
                !authority.endsWith(":443") ||
                !host.matches(Regex("[A-Za-z0-9.-]+")) || host.length > 253) {
                client.getOutputStream().write("HTTP/1.1 403 Forbidden\r\n\r\n".toByteArray())
                return
            }
            upstream = connect(host)
            val remote = upstream
            sockets.add(remote)
            if (closed) return
            remote.soTimeout = 20_000
            client.getOutputStream().apply {
                write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                flush()
            }
            workers.execute {
                try { input.copyTo(remote.getOutputStream()) } catch (_: Exception) {
                    // Closing either endpoint terminates both directions.
                } finally { remote.close(); client.close() }
            }
            remote.getInputStream().copyTo(client.getOutputStream())
        } catch (_: Exception) {
            // yt-dlp receives a connection failure and reports the platform error.
        } finally {
            upstream?.close()
            client.close()
            sockets.remove(client)
            upstream?.let { sockets.remove(it) }
        }
    }

    override fun close() {
        closed = true
        server.close()
        sockets.forEach { runCatching { it.close() } }
        workers.shutdownNow()
    }

    companion object {
        fun connectPublic(host: String, lookup: (String) -> List<InetAddress>): Socket {
            var failure: Exception? = null
            for (address in lookup(host)) {
                if (address.isAnyLocalAddress || address.isLoopbackAddress ||
                    address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) continue
                val socket = Socket()
                try {
                    socket.connect(InetSocketAddress(address, 443), 8_000)
                    return socket
                } catch (e: Exception) {
                    failure = e
                    socket.close()
                }
            }
            throw java.io.IOException("No reachable public address", failure)
        }
    }
}
