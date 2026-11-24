package dev.typetype.server.ai

import dev.typetype.server.cache.CacheService
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset

class ManagedAiKit(
    private val provider: AiProvider?,
    private val cache: CacheService?,
    private val rpmLimit: Int,
    private val dailyLimit: Int,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : AiKit {
    override val enabled: Boolean
        get() = provider != null

    override val providerId: String?
        get() = provider?.id

    override suspend fun complete(request: AiCompletionRequest): AiCompletionResult {
        val active = provider ?: return AiCompletionResult.Disabled
        if (request.userPrompt.isBlank()) {
            return AiCompletionResult.Failed("prompt_blank", retryable = false)
        }
        val activeCache = cache
        val namespacedKey = cacheKeyFor(active, request)
        if (namespacedKey != null && activeCache != null) {
            val cached = runCatching { activeCache.get(namespacedKey) }.getOrNull()
            if (cached != null) {
                return AiCompletionResult.Success(
                    text = cached,
                    model = active.model,
                    providerId = active.id,
                    cached = true,
                )
            }
        }
        checkLocalRateLimits()?.let { return it }
        val result = active.complete(request)
        if (result is AiCompletionResult.Success && namespacedKey != null && activeCache != null) {
            runCatching { activeCache.set(namespacedKey, result.text, request.cacheTtlSeconds) }
        }
        return result
    }

    private fun cacheKeyFor(active: AiProvider, request: AiCompletionRequest): String? {
        if (cache == null || request.cacheTtlSeconds <= 0L) return null
        val rawKey = request.cacheKey?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return "ai:v1:${active.id}:${active.model}:${sha256Hex(rawKey)}"
    }

    private suspend fun checkLocalRateLimits(): AiCompletionResult.RateLimited? {
        val activeCache = cache ?: return null
        val now = nowMillis()
        if (rpmLimit > 0) {
            val minuteBucket = now / MILLIS_PER_MINUTE
            val count = runCatching {
                activeCache.increment("ai:rl:rpm:$minuteBucket", RPM_BUCKET_TTL_SECONDS)
            }.getOrNull()
            if (count != null && count > rpmLimit) {
                return AiCompletionResult.RateLimited("local_rpm_limit_exceeded")
            }
        }
        if (dailyLimit > 0) {
            val day = Instant.ofEpochMilli(now).atOffset(ZoneOffset.UTC).toLocalDate().toString()
            val count = runCatching {
                activeCache.increment("ai:rl:day:$day", DAILY_BUCKET_TTL_SECONDS)
            }.getOrNull()
            if (count != null && count > dailyLimit) {
                return AiCompletionResult.RateLimited("local_daily_limit_exceeded")
            }
        }
        return null
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val MILLIS_PER_MINUTE: Long = 60_000L
        const val RPM_BUCKET_TTL_SECONDS: Long = 90L
        const val DAILY_BUCKET_TTL_SECONDS: Long = 90_000L
    }
}
