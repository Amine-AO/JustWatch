package com.justwatch.links

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class ExtractionProxyTest {
    @Test fun tunnelsBytesAndPassesHostnameToResolverConnection() {
        val loopback = InetAddress.getByName("127.0.0.1")
        val executor = Executors.newSingleThreadExecutor()
        try {
            ServerSocket(0, 1, loopback).use { origin ->
                val echo = executor.submit {
                    origin.accept().use { socket ->
                        socket.soTimeout = 3_000
                        val bytes = ByteArray(4)
                        java.io.DataInputStream(socket.getInputStream()).readFully(bytes)
                        socket.getOutputStream().write(bytes)
                    }
                }
                ExtractionProxy { host ->
                    assertEquals("www.instagram.com", host)
                    Socket(loopback, origin.localPort)
                }.use { proxy ->
                    val port = java.net.URI(proxy.url).port
                    Socket(loopback, port).use { client ->
                        client.soTimeout = 3_000
                        client.getOutputStream().write("CONNECT www.instagram.com:443 HTTP/1.1\r\nHost: www.instagram.com:443\r\n\r\n".toByteArray())
                        val input = client.getInputStream()
                        val header = StringBuilder()
                        while (!header.endsWith("\r\n\r\n")) {
                            val byte = input.read()
                            assertTrue(byte >= 0)
                            header.append(byte.toChar())
                        }
                        assertTrue(header.startsWith("HTTP/1.1 200"))
                        val payload = byteArrayOf(0, 1, -1, 42)
                        client.getOutputStream().write(payload)
                        val result = ByteArray(4)
                        java.io.DataInputStream(input).readFully(result)
                        assertArrayEquals(payload, result)
                    }
                }
                echo.get(3, TimeUnit.SECONDS)
            }
        } finally { executor.shutdownNow() }
    }

    @Test fun rejectsNonTlsRequestsWithoutConnecting() {
        ExtractionProxy { throw AssertionError("Must not connect") }.use { proxy ->
            Socket("127.0.0.1", java.net.URI(proxy.url).port).use { client ->
                client.soTimeout = 3_000
                client.getOutputStream().write("CONNECT www.tiktok.com:80 HTTP/1.1\r\n\r\n".toByteArray())
                assertTrue(client.getInputStream().bufferedReader().readLine().contains("403"))
            }
        }
    }

    @Test fun refusesPrivateDnsAnswers() {
        try {
            ExtractionProxy.connectPublic("example.test") {
                listOf(InetAddress.getByName("127.0.0.1"), InetAddress.getByName("192.168.1.1"))
            }
            fail("Private addresses must not be connected")
        } catch (_: java.io.IOException) { }
    }
}
