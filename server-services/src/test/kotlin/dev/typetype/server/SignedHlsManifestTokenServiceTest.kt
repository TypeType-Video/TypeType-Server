package dev.typetype.server

import dev.typetype.server.services.SignedHlsManifestTokenResult
import dev.typetype.server.services.SignedHlsManifestTokenService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SignedHlsManifestTokenServiceTest {
    @Test
    fun `token verifies expected payload`() {
        val service = SignedHlsManifestTokenService("secret", nowMillis = { 1_000L })
        val token = service.createToken("user-1", "https://youtube.com/watch?v=test", "fingerprint")
        val result = service.verify(token)
        assertTrue(result is SignedHlsManifestTokenResult.Valid)
        val payload = (result as SignedHlsManifestTokenResult.Valid).token
        assertEquals("user-1", payload.userId)
        assertEquals("https://youtube.com/watch?v=test", payload.videoUrl)
        assertEquals("fingerprint", payload.fingerprint)
        assertEquals(1_000L + SignedHlsManifestTokenService.TTL_SECONDS * 1_000L, payload.expiresAt)
    }

    @Test
    fun `token rejects tampered signature`() {
        val service = SignedHlsManifestTokenService("secret", nowMillis = { 1_000L })
        val token = service.createToken("user-1", "https://youtube.com/watch?v=test", "fingerprint")
        assertEquals(SignedHlsManifestTokenResult.Invalid, service.verify("${token.dropLast(1)}x"))
    }

    @Test
    fun `token remains valid for a long live session and then expires`() {
        var now = 1_000L
        val service = SignedHlsManifestTokenService("secret", nowMillis = { now })
        val token = service.createToken("user-1", "https://youtube.com/watch?v=test", "fingerprint")
        now = 1_801_000L
        assertTrue(service.verify(token) is SignedHlsManifestTokenResult.Valid)
        now = 1_000L + SignedHlsManifestTokenService.TTL_SECONDS * 1_000L
        assertEquals(SignedHlsManifestTokenResult.Expired, service.verify(token))
    }
}
