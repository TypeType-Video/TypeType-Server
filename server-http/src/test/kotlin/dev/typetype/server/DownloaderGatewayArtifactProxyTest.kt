package dev.typetype.server

import com.sun.net.httpserver.HttpServer
import dev.typetype.server.routes.downloaderGatewayRoutes
import dev.typetype.server.services.DownloaderGatewayService
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloaderGatewayArtifactProxyTest {
    @Test
    fun `direct artifact head preserves metadata without a response body`() = testApplication {
        val requestedMethod = AtomicReference<String>()
        val upstream = HttpServer.create(InetSocketAddress(0), 0)
        upstream.createContext("/jobs/test/artifact") { exchange ->
            requestedMethod.set(exchange.requestMethod)
            exchange.responseHeaders.add(HttpHeaders.ContentType, "video/mp4")
            exchange.responseHeaders.add(HttpHeaders.ContentLength, "4096")
            exchange.responseHeaders.add(HttpHeaders.AcceptRanges, "bytes")
            exchange.responseHeaders.add(HttpHeaders.ETag, "\"artifact-v1\"")
            exchange.responseHeaders.add(HttpHeaders.LastModified, "Sat, 22 Aug 2026 10:00:00 GMT")
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        upstream.start()
        application {
            routing { downloaderGatewayRoutes(DownloaderGatewayService("http://127.0.0.1:${upstream.address.port}")) }
        }

        try {
            val response = client.head("/downloader/jobs/test/artifact")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("HEAD", requestedMethod.get())
            assertEquals("4096", response.headers[HttpHeaders.ContentLength])
            assertEquals("bytes", response.headers[HttpHeaders.AcceptRanges])
            assertEquals("\"artifact-v1\"", response.headers[HttpHeaders.ETag])
            assertEquals("Sat, 22 Aug 2026 10:00:00 GMT", response.headers[HttpHeaders.LastModified])
            assertEquals("", response.bodyAsText())
        } finally {
            upstream.stop(0)
        }
    }

    @Test
    fun `internal artifact redirect streams range response`() = testApplication {
        val requestedRange = AtomicReference<String>()
        val upstream = HttpServer.create(InetSocketAddress(0), 0)
        upstream.createContext("/jobs/test/artifact") { exchange ->
            exchange.responseHeaders.add(HttpHeaders.Location, "http://typetype-garage:${upstream.address.port}/object")
            exchange.responseHeaders.add("X-TypeType-Artifact-Proxy", "1")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        upstream.createContext("/object") { exchange ->
            requestedRange.set(exchange.requestHeaders.getFirst(HttpHeaders.Range))
            val payload = "abc".toByteArray()
            exchange.responseHeaders.add(HttpHeaders.ContentType, "video/mp4")
            exchange.responseHeaders.add(HttpHeaders.ContentDisposition, "inline; filename=demo.mp4")
            exchange.responseHeaders.add(HttpHeaders.AcceptRanges, "bytes")
            exchange.responseHeaders.add(HttpHeaders.ContentRange, "bytes 0-2/6")
            exchange.sendResponseHeaders(206, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        upstream.start()

        val gateway = DownloaderGatewayService(
            baseUrl = "http://127.0.0.1:${upstream.address.port}",
            client = OkHttpClient.Builder().dns(testDns()).followRedirects(false).followSslRedirects(false).build(),
        )

        application {
            routing {
                downloaderGatewayRoutes(gateway)
            }
        }

        try {
            val response = client.get("/downloader/jobs/test/artifact") {
                header(HttpHeaders.Range.lowercase(), "bytes=0-2")
            }
            assertEquals(HttpStatusCode.PartialContent, response.status)
            assertEquals("bytes=0-2", requestedRange.get())
            assertEquals("3", response.headers[HttpHeaders.ContentLength])
            assertEquals("bytes", response.headers[HttpHeaders.AcceptRanges])
            assertEquals("bytes 0-2/6", response.headers[HttpHeaders.ContentRange])
            assertTrue(response.headers[HttpHeaders.ContentDisposition].orEmpty().startsWith("attachment"))
            assertEquals("abc", response.bodyAsText())
        } finally {
            upstream.stop(0)
        }
    }

    @Test
    fun `public artifact redirect remains external`() = testApplication {
        val upstream = HttpServer.create(InetSocketAddress(0), 0)
        upstream.createContext("/jobs/test/artifact") { exchange ->
            exchange.responseHeaders.add(HttpHeaders.Location, "https://downloads.example.com/object")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        upstream.start()
        val gateway = DownloaderGatewayService("http://127.0.0.1:${upstream.address.port}")
        application { routing { downloaderGatewayRoutes(gateway) } }
        val noRedirectClient = createClient { followRedirects = false }

        try {
            val response = noRedirectClient.get("/downloader/jobs/test/artifact")
            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("https://downloads.example.com/object", response.headers[HttpHeaders.Location])
        } finally {
            upstream.stop(0)
        }
    }

    @Test
    fun `direct artifact preserves unsatisfied range response`() = testApplication {
        val upstream = HttpServer.create(InetSocketAddress(0), 0)
        upstream.createContext("/jobs/test/artifact") { exchange ->
            exchange.responseHeaders.add(HttpHeaders.ContentRange, "bytes */6")
            exchange.responseHeaders.add(HttpHeaders.AcceptRanges, "bytes")
            exchange.sendResponseHeaders(416, -1)
            exchange.close()
        }
        upstream.start()
        application {
            routing { downloaderGatewayRoutes(DownloaderGatewayService("http://127.0.0.1:${upstream.address.port}")) }
        }

        try {
            val response = client.get("/downloader/jobs/test/artifact") {
                header(HttpHeaders.Range, "bytes=20-30")
            }
            assertEquals(HttpStatusCode.fromValue(416), response.status)
            assertEquals("bytes */6", response.headers[HttpHeaders.ContentRange])
            assertEquals("bytes", response.headers[HttpHeaders.AcceptRanges])
        } finally {
            upstream.stop(0)
        }
    }

    private fun testDns(): Dns = Dns { hostname ->
        if (hostname == "typetype-garage") listOf(InetAddress.getByName("127.0.0.1")) else Dns.SYSTEM.lookup(hostname)
    }
}
