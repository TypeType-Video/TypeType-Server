package dev.typetype.server

import dev.typetype.server.routes.copyProxyStream
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class ProxyResponseWriterTest {
    @Test
    fun `copies the whole upstream body`() = runBlocking {
        val body = ByteArray(200_000) { it.toByte() }
        val channel = ByteChannel()

        copyProxyStream(ByteArrayInputStream(body), channel)
        channel.flushAndClose()

        assertArrayEquals(body, channel.readRemaining().readByteArray())
    }

    @Test
    fun `closed downstream channel stops proxy copy`() {
        val channel = ByteChannel().also { it.cancel(IOException("Broken pipe")) }

        assertDoesNotThrow {
            runBlocking { copyProxyStream(ByteArrayInputStream(byteArrayOf(1)), channel) }
        }
    }

    @Test
    fun `upstream read errors propagate`() {
        val failure = IOException("upstream read failed")
        val source = object : InputStream() {
            override fun read(): Int = throw failure

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = throw failure
        }

        val thrown = assertThrows(IOException::class.java) {
            runBlocking { copyProxyStream(source, ByteChannel()) }
        }
        assertEquals(failure.message, thrown.message)
    }
}
