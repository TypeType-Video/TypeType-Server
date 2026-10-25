package dev.typetype.server.services

import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.models.ExtractionResult
import kotlinx.serialization.Serializable

@Serializable
data class PublicCacheBackoff(
    val failures: Int,
    val retryAt: Long,
    val message: String? = null,
    val code: String? = null,
    val kind: String? = null,
) {
    fun isActive(now: Long): Boolean = now < retryAt

    fun failure(): ExtractionResult.Failure? = message?.let {
        ExtractionResult.Failure(
            message = it,
            code = code ?: "error",
            kind = ExtractionFailureKind.entries.firstOrNull { entry -> entry.name == kind } ?: ExtractionFailureKind.Unknown,
        )
    }

    fun next(now: Long, failure: ExtractionResult.Failure?): PublicCacheBackoff {
        val count = failures + 1
        val delaySeconds = (BASE_DELAY_SECONDS shl (count - 1).coerceAtMost(MAX_SHIFT)).coerceAtMost(MAX_DELAY_SECONDS)
        return PublicCacheBackoff(count, now + delaySeconds * 1_000L, failure?.message, failure?.code, failure?.kind?.name)
    }

    companion object {
        const val BASE_DELAY_SECONDS = 30L
        const val MAX_DELAY_SECONDS = 600L
        const val RETENTION_SECONDS = 3_600L
        private const val MAX_SHIFT = 5

        val NONE: PublicCacheBackoff = PublicCacheBackoff(failures = 0, retryAt = 0L)
    }
}
