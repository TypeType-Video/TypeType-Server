package dev.typetype.server.ai

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class GeminiProviderTest {
    @Test
    fun `sends api key in header never query and parses text and json schema responses`() = runTest {
        val capturedPath = AtomicReference<String>()
        val capturedQuery = AtomicReference<String?>()
        val capturedHeader = AtomicReference<String?>()
        val capturedBody = AtomicReference<String>()
        val secret = "AQ.fake-secret-do-not-leak"

        val server = startServer { exchange ->
            capturedPath.set(exchange.requestURI.path)
            capturedQuery.set(exchange.requestURI.rawQuery)
            capturedHeader.set(exchange.requestHeaders.getFirst("x-goog-api-key"))
            capturedBody.set(exchange.requestBody.readAllBytes().toString(StandardCharsets.UTF_8))
            val reply = """{"candidates":[{"content":{"parts":[{"text":"{\"allow\":true}"}]}}]}"""
            respond(exchange, 200, reply)
        }

        try {
            val provider = GeminiProvider(
                config = GeminiConfig(
                    apiKey = secret,
                    model = "gemini-3.5-flash-lite",
                    baseUrl = "http://127.0.0.1:${server.address.port}",
                ),
                timeoutMs = 5_000L,
            )
            val schema = buildJsonObject { put("type", "OBJECT") }
            val result = provider.complete(
                AiCompletionRequest(
                    systemPrompt = "Return strict JSON",
                    userPrompt = "Classify video",
                    responseFormat = AiResponseFormat.Json(schema),
                    maxOutputTokens = 128,
                    temperature = 0.0,
                    images = listOf(AiImageInput("image/jpeg", "AQID")),
                ),
            )

            assertEquals(
                AiCompletionResult.Success("""{"allow":true}""", "gemini-3.5-flash-lite", "gemini", cached = false),
                result,
            )
            assertEquals("/v1beta/models/gemini-3.5-flash-lite:generateContent", capturedPath.get())
            assertNull(capturedQuery.get())
            assertEquals(secret, capturedHeader.get())

            val sent = Json.parseToJsonElement(capturedBody.get()).jsonObject
            assertEquals(
                "Return strict JSON",
                sent["systemInstruction"]?.jsonObject?.get("parts")?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content,
            )
            val userParts = sent["contents"]?.jsonArray?.get(0)?.jsonObject?.get("parts")?.jsonArray
            assertEquals("Classify video", userParts?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content)
            val inlineData = userParts?.get(1)?.jsonObject?.get("inlineData")?.jsonObject
            assertEquals("image/jpeg", inlineData?.get("mimeType")?.jsonPrimitive?.content)
            assertEquals("AQID", inlineData?.get("data")?.jsonPrimitive?.content)
            val genCfg = sent["generationConfig"]?.jsonObject
            assertEquals("application/json", genCfg?.get("responseMimeType")?.jsonPrimitive?.content)
            assertEquals("OBJECT", genCfg?.get("responseSchema")?.jsonObject?.get("type")?.jsonPrimitive?.content)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `maps rate limit auth bad request server errors empty body and redacts key`() = runTest {
        val secret = "AQ.super-secret-value"
        var nextStatus = 429
        var nextBody = """{"error":{"message":"quota hit"}}"""

        val server = startServer { exchange ->
            respond(exchange, nextStatus, nextBody)
        }

        try {
            val provider = GeminiProvider(
                config = GeminiConfig(
                    apiKey = secret,
                    model = "gemini-3.5-flash-lite",
                    baseUrl = "http://127.0.0.1:${server.address.port}",
                ),
                timeoutMs = 5_000L,
            )

            assertEquals(
                AiCompletionResult.RateLimited("upstream_rate_limited"),
                provider.complete(AiCompletionRequest("hello")),
            )

            nextStatus = 403
            nextBody = """{"error":{"message":"invalid key $secret"}}"""
            assertEquals(
                AiCompletionResult.Failed("upstream_auth_error", retryable = false),
                provider.complete(AiCompletionRequest("hello")),
            )

            nextStatus = 400
            nextBody = """{"error":{"message":"bad model for $secret"}}"""
            val badReq = provider.complete(AiCompletionRequest("hello")) as AiCompletionResult.Failed
            assertFalse(badReq.retryable)
            assertFalse(badReq.reason.contains(secret))
            assertTrue(badReq.reason.contains("[redacted]"))

            nextStatus = 503
            nextBody = """{"error":{"message":"overloaded"}}"""
            val serverErr = provider.complete(AiCompletionRequest("hello")) as AiCompletionResult.Failed
            assertTrue(serverErr.retryable)

            nextStatus = 200
            nextBody = """{"candidates":[]}"""
            assertEquals(
                AiCompletionResult.Failed("empty_response", retryable = false),
                provider.complete(AiCompletionRequest("hello")),
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `retries once on fresh connection when upstream drops connection`() = runTest {
        val connections = AtomicInteger(0)
        val server = ServerSocket(0, 4, InetAddress.getLoopbackAddress())
        val worker = thread(isDaemon = true) {
            server.accept().use { connections.incrementAndGet() }
            server.accept().use { socket ->
                connections.incrementAndGet()
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }
                val body = """{"candidates":[{"content":{"parts":[{"text":"{\"score\":88}"}]}}]}"""
                val header =
                    "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                        "Content-Length: ${body.toByteArray(StandardCharsets.UTF_8).size}\r\n" +
                        "Connection: close\r\n\r\n"
                socket.getOutputStream().write((header + body).toByteArray(StandardCharsets.UTF_8))
                socket.getOutputStream().flush()
            }
        }
        try {
            val provider = GeminiProvider(
                config = GeminiConfig(
                    apiKey = "AQ.test-key",
                    model = "gemini-3.5-flash-lite",
                    baseUrl = "http://127.0.0.1:${server.localPort}",
                ),
                timeoutMs = 5_000L,
            )
            val result = provider.complete(AiCompletionRequest("hello"))
            assertEquals(
                AiCompletionResult.Success("""{"score":88}""", "gemini-3.5-flash-lite", "gemini", cached = false),
                result,
            )
            assertEquals(2, connections.get())
        } finally {
            server.close()
            worker.join(2_000)
        }
    }

    private fun startServer(
        handler: (com.sun.net.httpserver.HttpExchange) -> Unit,
    ): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange -> handler(exchange) }
        server.start()
        return server
    }

    private fun respond(exchange: com.sun.net.httpserver.HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
