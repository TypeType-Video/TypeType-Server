package dev.typetype.server.services.device

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.DeviceLookupResponse
import dev.typetype.server.models.DeviceStartResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class DeviceAuthorizationService(
    private val cache: CacheService,
    secret: String,
    private val enabled: suspend () -> Boolean,
    private val ownerOf: suspend (String) -> String?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val keys = DeviceAuthorizationKeys(secret)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun start(deviceName: String?, client: String): DeviceStartResult {
        if (!enabled()) return DeviceStartResult.Disabled
        if (cache.increment(keys.starts(client), START_WINDOW_SECONDS) > START_LIMIT) return DeviceStartResult.RateLimited
        val createdAt = now()
        val deviceCode = DeviceCodes.deviceCode()
        val deviceHash = keys.hash(deviceCode)
        val userCode = reserveUserCode(deviceHash)
        val request = StoredRequest(
            DeviceCodes.deviceName(deviceName),
            keys.userCode(userCode),
            createdAt,
            createdAt + LIFETIME_SECONDS * 1000,
        )
        cache.set(keys.request(deviceHash), json.encodeToString(StoredRequest.serializer(), request), LIFETIME_SECONDS)
        return DeviceStartResult.Started(
            DeviceStartResponse(deviceCode, DeviceCodes.display(userCode), LIFETIME_SECONDS, INTERVAL_SECONDS),
        )
    }

    suspend fun lookup(userId: String, userCode: String, client: String): DeviceDecisionResult =
        withPendingRequest(userId, userCode, client) { deviceHash, request, normalized ->
            cache.set(keys.scanned(deviceHash), "1", request.remainingSeconds())
            DeviceDecisionResult.Found(
                DeviceLookupResponse(DeviceCodes.display(normalized), request.deviceName, request.createdAt, request.expiresAt),
            )
        }

    suspend fun approve(userId: String, userCode: String, client: String): DeviceDecisionResult =
        withPendingRequest(userId, userCode, client) { deviceHash, request, _ ->
            val owner = ownerOf(userId) ?: userId
            decide(deviceHash, request, "$APPROVED_PREFIX$owner", DeviceDecisionResult.Approved)
        }

    suspend fun deny(userId: String, userCode: String, client: String): DeviceDecisionResult =
        withPendingRequest(userId, userCode, client) { deviceHash, request, _ ->
            decide(deviceHash, request, DENIED, DeviceDecisionResult.Denied)
        }

    suspend fun poll(deviceCode: String): DevicePollResult {
        if (!enabled()) return DevicePollResult.Disabled
        if (!DeviceCodes.isDeviceCode(deviceCode)) return DevicePollResult.Invalid
        val deviceHash = keys.hash(deviceCode)
        val request = load(deviceHash) ?: return DevicePollResult.Expired
        if (request.isExpired()) {
            forget(deviceHash, request)
            return DevicePollResult.Expired
        }
        val polledAt = now()
        val previous = cache.get(keys.poll(deviceHash))?.toLongOrNull()
        cache.set(keys.poll(deviceHash), polledAt.toString(), request.remainingSeconds())
        if (previous != null && polledAt - previous < MIN_POLL_GAP_MS) return DevicePollResult.SlowDown
        val decision = cache.get(keys.decision(deviceHash))
            ?: return DevicePollResult.Pending(scanned = cache.get(keys.scanned(deviceHash)) != null)
        if (cache.getAndDelete(keys.request(deviceHash)) == null) return DevicePollResult.Expired
        forget(deviceHash, request)
        return if (decision.startsWith(APPROVED_PREFIX)) {
            DevicePollResult.Approved(decision.removePrefix(APPROVED_PREFIX))
        } else {
            DevicePollResult.Denied
        }
    }

    private suspend fun withPendingRequest(
        userId: String,
        userCode: String,
        client: String,
        block: suspend (String, StoredRequest, String) -> DeviceDecisionResult,
    ): DeviceDecisionResult {
        if (!enabled()) return DeviceDecisionResult.Disabled
        if (userId.startsWith(GUEST_PREFIX)) return DeviceDecisionResult.GuestForbidden
        if (isLocked(userId, client)) return DeviceDecisionResult.Locked
        val normalized = DeviceCodes.normalizeUserCode(userCode) ?: return failure(userId, client)
        val deviceHash = cache.get(keys.userCode(normalized)) ?: return failure(userId, client)
        val request = load(deviceHash)?.takeUnless { it.isExpired() } ?: return failure(userId, client)
        if (cache.get(keys.decision(deviceHash)) != null) return DeviceDecisionResult.AlreadyDecided
        return block(deviceHash, request, normalized)
    }

    private suspend fun decide(
        deviceHash: String,
        request: StoredRequest,
        value: String,
        result: DeviceDecisionResult,
    ): DeviceDecisionResult =
        if (cache.setIfAbsent(keys.decision(deviceHash), value, request.remainingSeconds())) {
            result
        } else {
            DeviceDecisionResult.AlreadyDecided
        }

    private suspend fun reserveUserCode(deviceHash: String): String {
        repeat(USER_CODE_ATTEMPTS) {
            val candidate = DeviceCodes.userCode()
            if (cache.setIfAbsent(keys.userCode(candidate), deviceHash, LIFETIME_SECONDS)) return candidate
        }
        throw IllegalStateException("Could not reserve a device user code")
    }

    private suspend fun isLocked(userId: String, client: String): Boolean =
        failures(keys.accountFailures(userId)) >= FAILURE_LIMIT || failures(keys.clientFailures(client)) >= FAILURE_LIMIT

    private suspend fun failures(key: String): Long = cache.get(key)?.toLongOrNull() ?: 0L

    private suspend fun failure(userId: String, client: String): DeviceDecisionResult {
        cache.increment(keys.accountFailures(userId), LOCK_SECONDS)
        cache.increment(keys.clientFailures(client), LOCK_SECONDS)
        return DeviceDecisionResult.NotFound
    }

    private suspend fun load(deviceHash: String): StoredRequest? =
        cache.get(keys.request(deviceHash))?.let {
            runCatching { json.decodeFromString(StoredRequest.serializer(), it) }.getOrNull()
        }

    private suspend fun forget(deviceHash: String, request: StoredRequest) {
        cache.delete(keys.request(deviceHash))
        cache.delete(request.userCodeKey)
        cache.delete(keys.decision(deviceHash))
        cache.delete(keys.scanned(deviceHash))
        cache.delete(keys.poll(deviceHash))
    }

    private fun StoredRequest.isExpired(): Boolean = now() >= expiresAt

    private fun StoredRequest.remainingSeconds(): Long = ((expiresAt - now()) / 1000).coerceAtLeast(1)

    @Serializable
    private data class StoredRequest(
        val deviceName: String,
        val userCodeKey: String,
        val createdAt: Long,
        val expiresAt: Long,
    )

    companion object {
        const val LIFETIME_SECONDS = 600L
        const val INTERVAL_SECONDS = 2L
        const val FAILURE_LIMIT = 5L
        const val START_LIMIT = 20L
        private const val START_WINDOW_SECONDS = 600L
        private const val LOCK_SECONDS = 900L
        private const val MIN_POLL_GAP_MS = 1_500L
        private const val USER_CODE_ATTEMPTS = 5
        private const val GUEST_PREFIX = "guest:"
        private const val APPROVED_PREFIX = "approved:"
        private const val DENIED = "denied"
    }
}
