package dev.typetype.server

import dev.typetype.server.models.PushDeviceRegistrationRequest
import dev.typetype.server.services.DeviceRegistrationResult
import dev.typetype.server.services.EndpointValidationResult
import dev.typetype.server.services.PushDeviceRegistry
import dev.typetype.server.services.UnifiedPushEndpointValidator
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetAddress

class PushDeviceRegistryTest {
    private val validator = UnifiedPushEndpointValidator {
        arrayOf(InetAddress.getByAddress(byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34)))
    }
    private val registry = PushDeviceRegistry(validator)

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() = TestDatabase.truncateAll()

    @Test
    fun `registration replaces one device and prevents endpoint sharing`() = runTest {
        val first = registry.register("user-a", request("device-a", "https://push.example/a"))
        assertTrue(first is DeviceRegistrationResult.Success)
        val replacement = registry.register("user-a", request("device-a", "https://push.example/b"))
        assertTrue(replacement is DeviceRegistrationResult.Success)
        assertEquals(1, registry.list("user-a").size)
        assertEquals(
            DeviceRegistrationResult.EndpointConflict,
            registry.register("user-b", request("device-b", "https://push.example/b")),
        )
    }

    private fun request(deviceId: String, endpoint: String) = PushDeviceRegistrationRequest(deviceId, endpoint = endpoint)
}
