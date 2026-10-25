package dev.typetype.server

import dev.typetype.server.routes.stopOnClientDisconnect
import dev.typetype.server.routes.writeLiveChat
import dev.typetype.server.services.YoutubeLiveChatEvent
import io.ktor.util.cio.ChannelWriteException
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.channels.ClosedChannelException

class StopOnClientDisconnectTest {
    @Test
    fun `stops quietly when the client channel is gone`() {
        listOf(
            ChannelWriteException("Cannot write to channel", ClosedChannelException()),
            ClosedChannelException(),
            IOException("Broken pipe"),
            IOException("Connection reset by peer"),
        ).forEach { error ->
            assertDoesNotThrow { runBlocking { stopOnClientDisconnect { throw error } } }
        }
    }

    @Test
    fun `keeps unrelated failures and cancellation`() {
        assertThrows(IOException::class.java) {
            runBlocking { stopOnClientDisconnect { throw IOException("SABR media segment ended early") } }
        }
        assertThrows(CancellationException::class.java) {
            runBlocking { stopOnClientDisconnect { throw CancellationException("cancelled") } }
        }
    }

    @Test
    fun `live chat stops when the client closed the stream`() {
        val channel = ByteChannel()
        runBlocking { channel.close() }

        assertDoesNotThrow {
            runBlocking { stopOnClientDisconnect { channel.writeLiveChat(flowOf(YoutubeLiveChatEvent.Heartbeat)) } }
        }
    }
}
