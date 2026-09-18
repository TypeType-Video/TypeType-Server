package dev.typetype.server

import dev.typetype.server.services.EndpointValidationResult
import dev.typetype.server.services.UnifiedPushEndpointValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress

class UnifiedPushEndpointValidatorTest {
    private val publicAddress = InetAddress.getByAddress(byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34))
    private val resolver = UnifiedPushEndpointValidator { arrayOf(publicAddress) }

    @Test
    fun `accepts https endpoint and rejects non https forms`() {
        assertTrue(resolver.validate("https://push.example.invalid/endpoint?id=1") is EndpointValidationResult.Valid)
        assertEquals("endpoint_scheme", reason(resolver.validate("http://push.example.invalid/endpoint")))
        assertEquals("endpoint_scheme", reason(resolver.validate("https://user:pass@push.example.invalid/endpoint")))
        assertEquals("endpoint_scheme", reason(resolver.validate("https://push.example.invalid/endpoint#fragment")))
    }

    @Test
    fun `rejects an address that resolves into a private network`() {
        val privateValidator = UnifiedPushEndpointValidator {
            arrayOf(InetAddress.getByAddress(byteArrayOf(10, 0, 0, 8)))
        }
        assertEquals("endpoint_private_address", reason(privateValidator.validate("https://push.example.invalid/endpoint")))
    }

    private fun reason(result: EndpointValidationResult): String =
        (result as EndpointValidationResult.Invalid).reason
}
