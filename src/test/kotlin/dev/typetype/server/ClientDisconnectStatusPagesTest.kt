package dev.typetype.server

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.cio.ChannelWriteException
import io.ktor.utils.io.ClosedByteChannelException
import io.ktor.utils.io.ClosedWriteChannelException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.channels.ClosedChannelException
import java.util.concurrent.ConcurrentHashMap

class ClientDisconnectStatusPagesTest {
    private val logger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
    private val events = ListAppender<ILoggingEvent>()
    private val finished = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>

    @BeforeEach
    fun start() {
        events.start()
        logger.addAppender(events)
        server = embeddedServer(Netty, port = 0) { installRoutes() }.start(wait = false)
    }

    @AfterEach
    fun stop() {
        server.stop(0L, 1_000L)
        logger.detachAppender(events)
    }

    @Test
    fun `client disconnects inside a streamed body never answer 500 nor log errors`() {
        DISCONNECTS.keys.filterNot { it == WRAPPED }.forEach { name ->
            assertNotEquals(HttpStatusCode.InternalServerError, request("/stream/$name"), name)
        }

        assertEquals(emptyList<String>(), errorMessages())
    }

    @Test
    fun `client disconnects before the response never answer 500 nor log errors`() {
        DISCONNECTS.keys.forEach { name -> assertNotEquals(HttpStatusCode.InternalServerError, request("/direct/$name"), name) }

        assertEquals(emptyList<String>(), errorMessages())
    }

    @Test
    fun `unrelated failures still answer 500 and log an error`() {
        assertEquals(HttpStatusCode.InternalServerError, request("/direct/unrelated"))
        assertEquals(1, errorMessages().size)
    }

    private fun Application.installRoutes() {
        intercept(ApplicationCallPipeline.Setup) {
            try {
                proceed()
            } finally {
                finished[call.request.path()]?.complete(Unit)
            }
        }
        install(ContentNegotiation) { json() }
        configureStatusPages()
        routing {
            get("/stream/{name}") {
                val error = failure(call.parameters["name"])
                call.respondOutputStream {
                    write(ByteArray(16))
                    flush()
                    throw error
                }
            }
            get("/direct/{name}") { throw failure(call.parameters["name"]) }
        }
    }

    private fun request(path: String): HttpStatusCode? = runBlocking {
        val done = CompletableDeferred<Unit>().also { finished[path] = it }
        val port = server.engine.resolvedConnectors().first().port
        val connection = URI("http://127.0.0.1:$port$path").toURL().openConnection() as HttpURLConnection
        val status = runCatching { connection.responseCode }.getOrNull()?.let(HttpStatusCode::fromValue)
        runCatching { connection.inputStream.use { it.readBytes() } }
        connection.disconnect()
        withTimeout(5_000L) { done.await() }
        status
    }

    private fun failure(name: String?): Throwable = DISCONNECTS[name]?.invoke() ?: IllegalStateException("boom")

    private fun errorMessages(): List<String> =
        events.list.filter { it.level == Level.ERROR }.map { "${it.loggerName}: ${it.formattedMessage}" }

    private companion object {
        const val WRAPPED = "wrapped-broken-pipe"
        val DISCONNECTS: Map<String, () -> Throwable> = mapOf(
            "channel-write" to { ChannelWriteException("Cannot write to channel", ClosedChannelException()) },
            "closed-byte-channel" to { ClosedByteChannelException(IOException("closed")) },
            "closed-write-channel" to { ClosedWriteChannelException() },
            "closed-nio-channel" to { ClosedChannelException() },
            "broken-pipe" to { IOException("Broken pipe") },
            "connection-reset" to { IOException("Connection reset by peer") },
            WRAPPED to { IllegalStateException("write failed", IOException("Broken pipe")) },
        )
    }
}
