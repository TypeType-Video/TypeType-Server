package dev.typetype.server.services.device

import dev.typetype.server.FakeCacheService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceAuthorizationServiceTest {
    private val cache = FakeCacheService()
    private var clock = 1_000_000L
    private var enabled = true
    private val owners = mapOf("profile-2" to "owner-1")
    private val service = DeviceAuthorizationService(
        cache = cache,
        secret = "test-secret",
        enabled = { enabled },
        ownerOf = { owners[it] },
        now = { clock },
    )

    private fun started(name: String? = "TypeType TV · Box") = runBlocking {
        (service.start(name, CLIENT) as DeviceStartResult.Started).response
    }

    @Test
    fun `start issues a long secret device code and a short readable user code`() {
        val response = started()
        assertEquals(43, response.deviceCode.length)
        assertTrue(Regex("^[${DeviceCodes.USER_CODE_ALPHABET}]{4}-[${DeviceCodes.USER_CODE_ALPHABET}]{4}$").matches(response.userCode))
        assertEquals(600, response.expiresIn)
        assertEquals(2, response.interval)
    }

    @Test
    fun `codes are never stored in clear`() = runBlocking {
        val response = started()
        service.lookup(USER, response.userCode, CLIENT)
        val compactUserCode = response.userCode.replace("-", "")
        val stored = cache.keys().flatMap { listOf(it, cache.get(it).orEmpty()) }
        assertFalse(stored.any { response.deviceCode in it || compactUserCode in it })
    }

    @Test
    fun `approved request hands tokens to the device exactly once`() = runBlocking {
        val response = started()
        assertEquals(DeviceDecisionResult.Approved, service.approve(USER, response.userCode, CLIENT))
        assertEquals(DevicePollResult.Approved(USER), service.poll(response.deviceCode))
        clock += 3_000
        assertEquals(DevicePollResult.Expired, service.poll(response.deviceCode))
        assertEquals(DeviceDecisionResult.NotFound, service.lookup(USER, response.userCode, CLIENT))
    }

    @Test
    fun `denied request ends with access denied`() = runBlocking {
        val response = started()
        assertEquals(DeviceDecisionResult.Denied, service.deny(USER, response.userCode, CLIENT))
        assertEquals(DevicePollResult.Denied, service.poll(response.deviceCode))
    }

    @Test
    fun `device sees when the code was opened on the phone`() = runBlocking {
        val response = started()
        assertEquals(DevicePollResult.Pending(scanned = false), service.poll(response.deviceCode))
        val found = service.lookup(USER, response.userCode, CLIENT) as DeviceDecisionResult.Found
        assertEquals("TypeType TV · Box", found.request.deviceName)
        assertEquals(response.userCode, found.request.userCode)
        clock += 2_000
        assertEquals(DevicePollResult.Pending(scanned = true), service.poll(response.deviceCode))
    }

    @Test
    fun `polling faster than the interval asks the device to slow down`() = runBlocking {
        val response = started()
        assertEquals(DevicePollResult.Pending(scanned = false), service.poll(response.deviceCode))
        clock += 500
        assertEquals(DevicePollResult.SlowDown, service.poll(response.deviceCode))
        clock += 2_000
        assertEquals(DevicePollResult.Pending(scanned = false), service.poll(response.deviceCode))
    }

    @Test
    fun `request expires after ten minutes`() = runBlocking {
        val response = started()
        clock += 600_000
        assertEquals(DeviceDecisionResult.NotFound, service.approve(USER, response.userCode, CLIENT))
        assertEquals(DevicePollResult.Expired, service.poll(response.deviceCode))
    }

    @Test
    fun `guests cannot approve a device`() = runBlocking {
        val response = started()
        assertEquals(DeviceDecisionResult.GuestForbidden, service.approve("guest:abc", response.userCode, CLIENT))
        assertEquals(DeviceDecisionResult.GuestForbidden, service.lookup("guest:abc", response.userCode, CLIENT))
    }

    @Test
    fun `five wrong codes lock the account even for the right code`() = runBlocking {
        val response = started()
        repeat(5) { assertEquals(DeviceDecisionResult.NotFound, service.lookup(USER, "BBBB-BBBB", CLIENT)) }
        assertEquals(DeviceDecisionResult.Locked, service.approve(USER, response.userCode, "other-client"))
    }

    @Test
    fun `five wrong codes from one client lock other accounts on that client`() = runBlocking {
        val response = started()
        repeat(5) { service.lookup("user-$it", "BBBB-BBBB", CLIENT) }
        assertEquals(DeviceDecisionResult.Locked, service.approve(USER, response.userCode, CLIENT))
    }

    @Test
    fun `disabled setting refuses every step`() = runBlocking {
        val response = started()
        enabled = false
        assertEquals(DeviceStartResult.Disabled, service.start("TV", CLIENT))
        assertEquals(DeviceDecisionResult.Disabled, service.approve(USER, response.userCode, CLIENT))
        assertEquals(DevicePollResult.Disabled, service.poll(response.deviceCode))
    }

    @Test
    fun `first decision wins`() = runBlocking {
        val response = started()
        assertEquals(DeviceDecisionResult.Approved, service.approve(USER, response.userCode, CLIENT))
        assertEquals(DeviceDecisionResult.AlreadyDecided, service.deny(USER, response.userCode, CLIENT))
        assertEquals(DeviceDecisionResult.AlreadyDecided, service.approve("someone-else", response.userCode, CLIENT))
    }

    @Test
    fun `profile approval signs the device into the owner account`() = runBlocking {
        val response = started()
        service.approve("profile-2", response.userCode, CLIENT)
        assertEquals(DevicePollResult.Approved("owner-1"), service.poll(response.deviceCode))
    }

    @Test
    fun `typed codes are accepted in lower case with spaces`() = runBlocking {
        val response = started()
        val typed = response.userCode.lowercase().replace("-", " ")
        assertTrue(service.lookup(USER, typed, CLIENT) is DeviceDecisionResult.Found)
    }

    @Test
    fun `malformed device codes are rejected without a lookup`() = runBlocking {
        assertEquals(DevicePollResult.Invalid, service.poll("short"))
    }

    @Test
    fun `one client cannot start more than twenty requests in ten minutes`() = runBlocking {
        repeat(20) { assertTrue(service.start("TV", CLIENT) is DeviceStartResult.Started) }
        assertEquals(DeviceStartResult.RateLimited, service.start("TV", CLIENT))
        assertTrue(service.start("TV", "other-client") is DeviceStartResult.Started)
    }

    @Test
    fun `device names are cleaned and capped`() {
        assertEquals("TypeType TV", DeviceCodes.deviceName("  \u0000‮ "))
        assertEquals("Living room TV", DeviceCodes.deviceName(" Living \n room\tTV "))
        assertEquals(64, DeviceCodes.deviceName("x".repeat(200)).length)
    }

    private companion object {
        const val USER = "user-1"
        const val CLIENT = "203.0.113.7"
    }
}
