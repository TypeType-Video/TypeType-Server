package dev.typetype.server

import com.sun.net.httpserver.HttpServer
import dev.typetype.server.downloader.OkHttpDownloader
import dev.typetype.server.downloader.YoutubeAuthUserContext
import dev.typetype.server.downloader.normalizeExtractorUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as ExtractorRequest
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OkHttpDownloaderCoreTest {
    @Test
    fun `YouTube auth user is limited to InnerTube requests`() {
        YoutubeAuthUserContext.set(3)
        try {
            assertEquals("3", YoutubeAuthUserContext.headerFor("https://www.youtube.com/youtubei/v1/player"))
            assertEquals(null, YoutubeAuthUserContext.headerFor("https://www.youtube.com/watch?v=test"))
            assertEquals(null, YoutubeAuthUserContext.headerFor("https://example.com/youtubei/v1/player"))
        } finally {
            YoutubeAuthUserContext.set(null)
        }
    }

    @Test
    fun `execute maps http response payload`() {
        val server = server(status = 200, body = "ok")
        server.start()
        try {
            val request = ExtractorRequest.newBuilder().get(urlOf(server, "/ok")).build()
            val response = OkHttpDownloader.instance().execute(request)
            assertEquals(200, response.responseCode())
            assertEquals("ok", response.responseBody())
            assertTrue(response.latestUrl().contains("/ok"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `execute throws recaptcha on 429`() {
        val server = server(status = 429, body = "blocked")
        server.start()
        try {
            val request = ExtractorRequest.newBuilder().get(urlOf(server, "/blocked")).build()
            var thrown: Throwable? = null
            runCatching { OkHttpDownloader.instance().execute(request) }.onFailure { thrown = it }
            assertEquals(ReCaptchaException::class.java, thrown?.javaClass)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a dozen requests sent together all reach the same host at once`() {
        val together = 12
        val arrived = CountDownLatch(together)
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.executor = Executors.newFixedThreadPool(together)
        server.createContext("/") { exchange ->
            arrived.countDown()
            val all = arrived.await(5, TimeUnit.SECONDS)
            exchange.sendResponseHeaders(if (all) 200 else 503, -1)
            exchange.close()
        }
        server.start()
        try {
            val downloader = OkHttpDownloader.instance()
            val answers = java.util.concurrent.ConcurrentLinkedQueue<Int>()
            val done = CountDownLatch(together)
            repeat(together) { index ->
                val request = ExtractorRequest.newBuilder().get(urlOf(server, "/video/$index")).build()
                downloader.executeAsync(request, object : Downloader.AsyncCallback {
                    override fun onSuccess(response: Response) { answers += response.responseCode(); done.countDown() }
                    override fun onError(error: Exception) { answers += -1; done.countDown() }
                })
            }
            assertTrue(done.await(10, TimeUnit.SECONDS))
            assertEquals(List(together) { 200 }, answers.toList())
        } finally {
            server.stop(0)
            (server.executor as java.util.concurrent.ExecutorService).shutdownNow()
        }
    }

    @Test
    fun `the number of requests sent together to one host can be set and falls back when unreadable`() {
        assertEquals(64, OkHttpDownloader.simultaneousPerHost(null))
        assertEquals(8, OkHttpDownloader.simultaneousPerHost("8"))
        assertEquals(64, OkHttpDownloader.simultaneousPerHost("0"))
        assertEquals(64, OkHttpDownloader.simultaneousPerHost("many"))
    }

    @Test
    fun `one request per connection is asked for with 1 and nothing else`() {
        assertEquals(false, OkHttpDownloader.oneRequestPerConnection(null))
        assertEquals(true, OkHttpDownloader.oneRequestPerConnection("1"))
        assertEquals(false, OkHttpDownloader.oneRequestPerConnection("2"))
    }

    @Test
    fun `the text of a subtitle track is never asked to the provider`() {
        val asked = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            asked += exchange.requestURI.path
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        try {
            val subtitle = ExtractorRequest.newBuilder().get(urlOf(server, "/api/timedtext?v=abc&lang=en")).build()
            var thrown: Throwable? = null
            runCatching { OkHttpDownloader.instance().execute(subtitle) }.onFailure { thrown = it }
            assertEquals(java.io.IOException::class.java, thrown?.javaClass)
            OkHttpDownloader.instance().execute(ExtractorRequest.newBuilder().get(urlOf(server, "/youtubei/v1/player")).build())
            assertEquals(listOf("/youtubei/v1/player"), asked)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `executeAsync invokes success callback and finishes call`() {
        val server = server(status = 200, body = "async")
        server.start()
        try {
            val request = ExtractorRequest.newBuilder().get(urlOf(server, "/async")).build()
            val latch = CountDownLatch(1)
            var status: Int? = null
            var error: Exception? = null
            val call = OkHttpDownloader.instance().executeAsync(request, object : Downloader.AsyncCallback {
                override fun onSuccess(response: Response) {
                    status = response.responseCode()
                    latch.countDown()
                }

                override fun onError(e: Exception) {
                    error = e
                    latch.countDown()
                }
            })
            assertTrue(latch.await(3, TimeUnit.SECONDS))
            assertEquals(200, status)
            assertNull(error)
            assertTrue(waitUntilFinished(call))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `normalizes youtube relative extractor urls`() {
        assertEquals("https://www.youtube.com/api/timedtext", normalizeExtractorUrl("/api/timedtext"))
        assertEquals("https://example.test/x", normalizeExtractorUrl("https://example.test/x"))
    }

    @Test
    fun `evictIdleConnections completes without error after request`() {
        val server = server(status = 200, body = "ok")
        server.start()
        try {
            val downloader = OkHttpDownloader.instance()
            val request = ExtractorRequest.newBuilder().get(urlOf(server, "/pool")).build()
            assertEquals(200, downloader.execute(request).responseCode())
            downloader.evictIdleConnections()
        } finally {
            server.stop(0)
        }
    }

    private fun waitUntilFinished(call: org.schabi.newpipe.extractor.downloader.CancellableCall): Boolean {
        repeat(50) {
            if (call.isFinished()) return true
            Thread.sleep(20)
        }
        return false
    }

    private fun urlOf(server: HttpServer, path: String): String =
        "http://127.0.0.1:${server.address.port}$path"

    private fun server(status: Int, body: String): HttpServer {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        return server
    }
}
