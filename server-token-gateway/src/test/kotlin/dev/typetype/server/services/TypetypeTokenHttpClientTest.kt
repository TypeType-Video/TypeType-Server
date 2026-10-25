package dev.typetype.server.services

import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.net.ProxySelector
import java.util.concurrent.TimeUnit

class TypetypeTokenHttpClientTest {
    @Test
    fun `bounds connect read and call time`() {
        val client = TypetypeTokenHttpClient.bounded(OkHttpClient())

        assertEquals(TypetypeTokenHttpClient.CONNECT_TIMEOUT_MS.toInt(), client.connectTimeoutMillis)
        assertEquals(TypetypeTokenHttpClient.READ_TIMEOUT_MS.toInt(), client.readTimeoutMillis)
        assertEquals(TypetypeTokenHttpClient.CALL_TIMEOUT_MS.toInt(), client.callTimeoutMillis)
    }

    @Test
    fun `keeps the egress proxy selector of the base client`() {
        val selector = ProxySelector.of(null)
        val client = TypetypeTokenHttpClient.bounded(OkHttpClient.Builder().proxySelector(selector).build())

        assertSame(selector, client.proxySelector)
    }

    @Test
    fun `keeps a client that already has a call timeout`() {
        val base = OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build()

        assertSame(base, TypetypeTokenHttpClient.bounded(base))
    }

    @Test
    fun `token clients never use an unbounded client`() {
        val sabrClient = TypetypeTokenSabrTokenClient("http://token.invalid", OkHttpClient())
        val sessionClient = TypetypeTokenYoutubeSessionClient("http://token.invalid", OkHttpClient())

        assertEquals(TypetypeTokenHttpClient.CALL_TIMEOUT_MS.toInt(), sabrClient.client.callTimeoutMillis)
        assertEquals(TypetypeTokenHttpClient.CALL_TIMEOUT_MS.toInt(), sessionClient.client.callTimeoutMillis)
        assertEquals(
            TypetypeTokenHttpClient.CALL_TIMEOUT_MS.toInt(),
            TypetypeTokenSabrTokenClient("http://token.invalid").client.callTimeoutMillis,
        )
    }
}
