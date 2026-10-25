package dev.typetype.server

import dev.typetype.server.downloader.YoutubeProxySelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.ServerSocket

class YoutubeEgressClientsTest {
    @Test
    fun `tv player requests leave through the configured YouTube proxy`() = runBlocking {
        ServerSocket(0).use { proxy ->
            proxy.soTimeout = 5_000
            val egress = YoutubeEgressClients(YoutubeProxySelector.fromUrl("http://127.0.0.1:${proxy.localPort}"))
            val firstLine = async(Dispatchers.IO) {
                proxy.accept().use { socket -> socket.getInputStream().bufferedReader().readLine() }
            }

            egress.tvPlayerClient().player("video-id", "access-token")

            assertTrue(firstLine.await().startsWith("CONNECT www.youtube.com:443"))
        }
    }

    @Test
    fun `youtube api and token clients have a call timeout`() {
        val egress = YoutubeEgressClients(null)

        assertEquals(30_000, egress.youtubeApi.callTimeoutMillis)
        assertEquals(20_000, egress.token.callTimeoutMillis)
    }
}
