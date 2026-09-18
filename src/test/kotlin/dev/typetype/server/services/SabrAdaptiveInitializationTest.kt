package dev.typetype.server.services

import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

class DownloaderGatewayServiceTest {
    @Test
    fun `stream response ignores regular client read timeout`() {
        val upstream = HttpServer.create(InetSocketAddress(0), 0)
        upstream.createContext("/events") { exchange ->
            val payload = "data: ready\n\n".toByteArray()
            exchange.sendResponseHeaders(200, payload.size.toLong())
            Thread.sleep(150)
            exchange.responseBody.use { it.write(payload) }
        }
        upstream.start()
        try {
            val client = OkHttpClient.Builder().readTimeout(50, TimeUnit.MILLISECONDS).build()
            val gateway = DownloaderGatewayService("http://127.0.0.1:${upstream.address.port}", client)

            gateway.openStream("GET", "/events", null, emptyMap(), null).use { response ->
                assertEquals("data: ready\n\n", response.body.string())
            }
        } finally {
            upstream.stop(0)
        }
    }
}
