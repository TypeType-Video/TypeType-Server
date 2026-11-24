package dev.typetype.server.ai

import dev.typetype.server.FakeCacheService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class AiKitConfigTest {
    @Test
    fun `defaults to disabled and standard limits when unset`() = runTest {
        val kitConfig = AiKitConfig.fromEnvironment { null }
        val geminiConfig = GeminiConfig.fromEnvironment(env = { null }, secret = { null })
        val kit = AiKitFactory.fromEnvironment(
            cache = FakeCacheService(),
            env = { null },
            secret = { null },
        )

        assertNull(kitConfig.provider)
        assertNull(kitConfig.outboundProxyUrl)
        assertEquals(AiKitConfig.DEFAULT_TIMEOUT_MS, kitConfig.timeoutMs)
        assertEquals(AiKitConfig.DEFAULT_REQUESTS_PER_MINUTE, kitConfig.requestsPerMinute)
        assertEquals(AiKitConfig.DEFAULT_DAILY_REQUEST_LIMIT, kitConfig.dailyRequestLimit)
        assertFalse(geminiConfig.enabled)
        assertEquals(GeminiConfig.DEFAULT_MODEL, geminiConfig.model)
        assertEquals(GeminiConfig.DEFAULT_BASE_URL, geminiConfig.baseUrl)
        assertFalse(kit.enabled)
        assertNull(kit.providerId)
        assertEquals(AiCompletionResult.Disabled, kit.complete(AiCompletionRequest("hello")))
    }

    @Test
    fun `accepts AQ and AIza key formats trims base url and clamps timeout`() {
        val env = mapOf(
            "AI_PROVIDER" to " GEMINI ",
            "AI_OUTBOUND_PROXY_URL" to " http://127.0.0.1:10808 ",
            "AI_TIMEOUT_MS" to "999999",
            "AI_REQUESTS_PER_MINUTE" to "10",
            "AI_DAILY_REQUEST_LIMIT" to "50",
            "AI_GEMINI_MODEL" to " gemini-2.5-flash-lite ",
            "AI_GEMINI_BASE_URL" to " https://gateway.example.invalid/// ",
        )
        val kitConfig = AiKitConfig.fromEnvironment(env::get)
        val aqConfig = GeminiConfig.fromEnvironment(env = env::get, secret = { " AQ.test-key-123 " })
        val aizaConfig = GeminiConfig.fromEnvironment(env = env::get, secret = { "AIzaTestKey123" })

        assertEquals("gemini", kitConfig.provider)
        assertEquals("http://127.0.0.1:10808", kitConfig.outboundProxyUrl)
        assertEquals(AiKitConfig.MAX_TIMEOUT_MS, kitConfig.timeoutMs)
        assertEquals(10, kitConfig.requestsPerMinute)
        assertEquals(50, kitConfig.dailyRequestLimit)
        assertTrue(aqConfig.enabled)
        assertEquals("AQ.test-key-123", aqConfig.apiKey)
        assertEquals("gemini-2.5-flash-lite", aqConfig.model)
        assertEquals("https://gateway.example.invalid", aqConfig.baseUrl)
        assertTrue(aizaConfig.enabled)

        val lowTimeout = AiKitConfig.fromEnvironment(mapOf("AI_TIMEOUT_MS" to "10")::get)
        assertEquals(AiKitConfig.MIN_TIMEOUT_MS, lowTimeout.timeoutMs)
    }

    @Test
    fun `supports file-backed secret loading and disables on unknown provider or blank key`() {
        val secretFile = Files.createTempFile("ai-gemini-key-", ".txt")
        try {
            Files.writeString(secretFile, " AQ.file-secret-token \n")
            val env = mapOf(
                "AI_GEMINI_API_KEY_FILE" to secretFile.toString(),
                "AI_PROVIDER" to "gemini",
            )
            val fileBacked = AiKitFactory.fromEnvironment(
                cache = FakeCacheService(),
                env = env::get,
                secret = { name -> env["${name}_FILE"]?.let { Files.readString(java.nio.file.Path.of(it)).trim() } },
            )
            assertTrue(fileBacked.enabled)
            assertEquals("gemini", fileBacked.providerId)

            val unknownProvider = AiKitFactory.fromEnvironment(
                cache = FakeCacheService(),
                env = mapOf("AI_PROVIDER" to "openai")::get,
                secret = { "AQ.file-secret-token" },
            )
            assertFalse(unknownProvider.enabled)
            assertNull(unknownProvider.providerId)

            val blankKey = GeminiConfig.fromEnvironment(env = { null }, secret = { "   " })
            assertFalse(blankKey.enabled)
        } finally {
            Files.deleteIfExists(secretFile)
        }
    }
}
