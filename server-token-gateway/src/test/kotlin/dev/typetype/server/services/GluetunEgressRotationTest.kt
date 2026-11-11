package dev.typetype.server.services

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

class GluetunEgressRotationTest {
    private val calls = CopyOnWriteArrayList<String>()
    private var status = 200
    private var time = 0L
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/v1/vpn/status") { exchange ->
            val body = exchange.requestBody.readBytes().decodeToString()
            calls += "${exchange.requestMethod} ${exchange.requestHeaders.getFirst("X-API-Key")} $body"
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
        }
        start()
    }

    private fun rotation() = GluetunEgressRotation(
        controlUrl = "http://127.0.0.1:${server.address.port}/",
        apiKey = "key",
        now = { time },
        settle = {},
    )

    @AfterEach
    fun stop() = server.stop(0)

    @Test
    fun `a single refusal leaves the exit alone and a second one moves it`() = runTest {
        val rotation = rotation()

        assertFalse(rotation.refused("sabr_attestation"))
        assertEquals(emptyList<String>(), calls)
        time = 30_000
        assertTrue(rotation.refused("sabr_attestation"))

        assertEquals(listOf("""PUT key {"status":"stopped"}""", """PUT key {"status":"running"}"""), calls)
    }

    @Test
    fun `refusals far apart never move the exit`() = runTest {
        val rotation = rotation()
        listOf(0L, 200_000L, 400_000L).forEach { at ->
            time = at
            assertFalse(rotation.refused("sabr_attestation"))
        }
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `a fresh exit is kept for ten minutes`() = runTest {
        val rotation = rotation()
        assertTrue(rotation.rotate("bot_check"))
        time = 599_000
        assertFalse(rotation.rotate("bot_check"))
        time = 600_000
        assertTrue(rotation.rotate("bot_check"))
        assertEquals(4, calls.size)
    }

    @Test
    fun `a refused control call reports the rotation as not done`() = runTest {
        status = 401
        assertFalse(rotation().rotate("bot_check"))
        assertEquals(1, calls.size)
    }

    @Test
    fun `it stays off without a control url and key`() {
        assertEquals(null, GluetunEgressRotation.fromEnvironment { null })
        val env = mapOf("YOUTUBE_EGRESS_GLUETUN_URL" to "http://gluetun:8000", "YOUTUBE_EGRESS_GLUETUN_API_KEY" to "k")
        assertTrue(GluetunEgressRotation.fromEnvironment(env::get) != null)
    }
}
