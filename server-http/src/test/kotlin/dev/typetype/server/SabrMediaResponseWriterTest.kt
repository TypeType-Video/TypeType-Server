package dev.typetype.server

import dev.typetype.server.routes.respondSabrMediaBytes
import dev.typetype.server.routes.respondSabrMediaStream
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

class SabrMediaResponseWriterTest {
    @Test
    fun `sabr media bytes include fixed length and ranges`() = testApplication {
        installApp()

        val response = client.get("/media")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("bytes", response.headers[HttpHeaders.AcceptRanges])
        assertEquals("10", response.headers[HttpHeaders.ContentLength])
        assertArrayEquals(ByteArray(10) { it.toByte() }, response.bodyAsBytes())
    }

    @Test
    fun `sabr media bytes honor byte ranges`() = testApplication {
        installApp()

        val response = client.get("/media") { header(HttpHeaders.Range, "bytes=2-5") }

        assertEquals(HttpStatusCode.PartialContent, response.status)
        assertEquals("bytes", response.headers[HttpHeaders.AcceptRanges])
        assertEquals("bytes 2-5/10", response.headers[HttpHeaders.ContentRange])
        assertEquals("4", response.headers[HttpHeaders.ContentLength])
        assertArrayEquals(byteArrayOf(2, 3, 4, 5), response.bodyAsBytes())
    }

    @Test
    fun `sabr media stream honors byte ranges without buffering the body`() = testApplication {
        val opened = AtomicInteger()
        installApp(opened)

        val response = client.get("/stream") { header(HttpHeaders.Range, "bytes=3-7") }

        assertEquals(HttpStatusCode.PartialContent, response.status)
        assertEquals("bytes 3-7/10", response.headers[HttpHeaders.ContentRange])
        assertEquals("5", response.headers[HttpHeaders.ContentLength])
        assertArrayEquals(byteArrayOf(3, 4, 5, 6, 7), response.bodyAsBytes())
        assertEquals(1, opened.get())
    }

    private fun ApplicationTestBuilder.installApp(opened: AtomicInteger = AtomicInteger()): Unit = application {
        routing {
            get("/media") {
                call.respondSabrMediaBytes("video/mp4; codecs=\"avc1.640028\"", ByteArray(10) { it.toByte() })
            }
            get("/stream") {
                call.respondSabrMediaStream(
                    "video/mp4; codecs=\"avc1.640028\"",
                    10L,
                    { ByteArrayInputStream(ByteArray(10) { it.toByte() }) },
                    opened::incrementAndGet,
                )
            }
        }
    }
}
