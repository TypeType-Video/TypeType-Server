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
        createContext("/v1/vpn/settings") { exchange ->
            val body = exchange.requestBody.readBytes().decodeToString()
            connected += SERVER_NAME.find(body)?.groupValues?.get(1).orEmpty()
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
        }
        start()
    }
    private val connected = CopyOnWriteArrayList<String>()

    private fun rotation(servers: List<String> = emptyList()) = GluetunEgressRotation(
        control = GluetunControl("http://127.0.0.1:${server.address.port}/", "key"),
        servers = servers,
        now = { time },
        settle = {},
        pick = { it.first() },
    )

    private class Check(private val good: Set<String>, private val connected: List<String>) : EgressExitCheck {
        val renewed = mutableListOf<String>()
        override suspend fun renew(videoId: String) { renewed += connected.last() }
        override suspend fun accepts(videoId: String): Boolean = connected.last() in good
    }

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
    fun `a named exit only stays once the check accepts it with a new token session`() = runTest {
        val rotation = rotation(listOf("FR#1", "FR#2", "FR#3"))
        val check = Check(good = setOf("FR#2"), connected).also { rotation.exitCheck = it }

        assertTrue(rotation.rotate("sabr_attestation", "video"))

        assertEquals(listOf("FR#1", "FR#2"), connected)
        assertEquals(listOf("FR#1", "FR#2"), check.renewed)
    }

    @Test
    fun `it goes back to the last accepted exit when no candidate passes`() = runTest {
        val rotation = rotation(listOf("FR#1", "FR#2", "FR#3", "FR#4"))
        val good = mutableSetOf("FR#1")
        rotation.exitCheck = Check(good, connected)
        assertTrue(rotation.rotate("sabr_attestation", "video"))
        good.clear()
        time = 600_000

        assertFalse(rotation.rotate("sabr_attestation", "video"))

        assertEquals(listOf("FR#1", "FR#2", "FR#3", "FR#4", "FR#1"), connected)
    }

    @Test
    fun `an exit refused lately is skipped`() = runTest {
        val rotation = rotation(listOf("FR#1", "FR#2", "FR#3", "FR#4"))
        rotation.exitCheck = Check(setOf("FR#2", "FR#3"), connected)
        assertTrue(rotation.rotate("sabr_attestation", "video"))
        time = 600_000

        assertTrue(rotation.rotate("sabr_attestation", "video"))

        assertEquals(listOf("FR#1", "FR#2", "FR#3"), connected)
    }

    @Test
    fun `it reads the named exits from the environment`() {
        val env = mapOf(
            "YOUTUBE_EGRESS_GLUETUN_URL" to "http://gluetun:8000",
            "YOUTUBE_EGRESS_GLUETUN_API_KEY" to "k",
            "YOUTUBE_EGRESS_GLUETUN_SERVERS" to " FR#109, DE#186 ,",
        )
        assertTrue(GluetunEgressRotation.fromEnvironment(env::get) != null)
    }

    @Test
    fun `it stays off without a control url and key`() {
        assertEquals(null, GluetunEgressRotation.fromEnvironment { null })
        val env = mapOf("YOUTUBE_EGRESS_GLUETUN_URL" to "http://gluetun:8000", "YOUTUBE_EGRESS_GLUETUN_API_KEY" to "k")
        assertTrue(GluetunEgressRotation.fromEnvironment(env::get) != null)
    }

    private companion object {
        val SERVER_NAME = Regex("\"names\":\\[\"([^\"]+)\"")
    }
}
