package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import dev.typetype.server.routes.respondProxyResult
import io.ktor.client.request.prepareGet
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

class ProxySlowReaderThreadTest {
    @Test
    fun `slow readers do not each pin a thread while the proxy waits for them`() = testApplication {
        val opened = AtomicInteger()
        application {
            routing {
                get("/proxy") {
                    opened.incrementAndGet()
                    call.respondProxyResult(ExtractionResult.Success(endlessProxy()))
                }
            }
        }
        startApplication()
        val pinned = coroutineScope {
            val release = CompletableDeferred<Unit>()
            val readers = List(SLOW_READERS) {
                launch { client.prepareGet("/proxy").execute { release.await() } }
            }
            withTimeout(10_000L) { while (opened.get() < SLOW_READERS) delay(10L) }
            delay(SETTLE_MS)
            threadsCopyingProxyBodies().also {
                release.complete(Unit)
                readers.forEach { reader -> reader.cancel() }
            }
        }

        assertTrue(pinned <= MAX_PINNED_THREADS, "threads pinned by slow readers: $pinned")
    }

    private fun endlessProxy(): ProxyResponse = ProxyResponse(
        status = 200,
        contentType = "video/mp4",
        contentLength = null,
        contentRange = null,
        acceptRanges = null,
        stream = EndlessStream(),
        close = {},
    )

    private fun threadsCopyingProxyBodies(): Int = Thread.getAllStackTraces().values.count { frames ->
        frames.any { it.className.startsWith(PROXY_WRITER_CLASS) }
    }

    private class EndlessStream : InputStream() {
        override fun read(): Int = 0

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            buffer.fill(0, offset, offset + length)
            return length
        }
    }

    private companion object {
        const val SLOW_READERS = 24
        const val MAX_PINNED_THREADS = 2
        const val SETTLE_MS = 1_000L
        const val PROXY_WRITER_CLASS = "dev.typetype.server.routes.ProxyResponseWriterKt"
    }
}
