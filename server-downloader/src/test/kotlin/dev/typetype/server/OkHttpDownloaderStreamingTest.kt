package dev.typetype.server

import com.sun.net.httpserver.HttpServer
import dev.typetype.server.downloader.OkHttpDownloader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class OkHttpDownloaderStreamingTest {
    @Test
    fun `postStreaming streams post response and keeps request headers`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val receivedBody = AtomicReference("")
        val receivedHeader = AtomicReference<String?>(null)
        val latch = CountDownLatch(1)
        server.createContext("/stream") { exchange ->
            receivedBody.set(exchange.requestBody.readBytes().toString(Charsets.UTF_8))
            receivedHeader.set(exchange.requestHeaders.getFirst("X-Test"))
            exchange.responseHeaders.add("Content-Type", "application/vnd.yt-ump")
            val bytes = "stream-ok".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            latch.countDown()
        }
        server.start()
        try {
            OkHttpDownloader.instance().postStreaming(
                url(server, "/stream"),
                mutableMapOf("X-Test" to mutableListOf("sabr")),
                "payload".toByteArray(),
                null,
            ).use { response ->
                assertEquals(200, response.responseCode())
                assertEquals("application/vnd.yt-ump", response.getHeader("Content-Type"))
                assertEquals("stream-ok", response.body().readBytes().toString(Charsets.UTF_8))
            }
            assertTrue(latch.await(3, TimeUnit.SECONDS))
            assertEquals("payload", receivedBody.get())
            assertEquals("sabr", receivedHeader.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `postStreaming times out stalled response bodies independently`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/stalled") { exchange ->
            exchange.sendResponseHeaders(200, 4L)
            Thread.sleep(500)
            runCatching { exchange.responseBody.use { it.write("late".toByteArray()) } }
        }
        server.start()
        try {
            val started = TimeSource.Monotonic.markNow()
            val error = runCatching {
                OkHttpDownloader.create(100).postStreaming(url(server, "/stalled"), null, null, null).use {
                    it.body().readBytes()
                }
            }.exceptionOrNull()

            assertTrue(error is SocketTimeoutException)
            assertTrue(started.elapsedNow() < 2.seconds)
        } finally {
            server.stop(0)
        }
    }

    private fun url(server: HttpServer, path: String): String =
        "http://127.0.0.1:${server.address.port}$path"
}
