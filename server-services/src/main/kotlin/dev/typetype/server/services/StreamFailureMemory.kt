package dev.typetype.server.services

import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.models.ExtractionResult
import java.util.concurrent.ConcurrentHashMap

class StreamFailureMemory(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val entries = ConcurrentHashMap<String, Entry>()

    fun recall(key: String): ExtractionResult<Nothing>? {
        val entry = entries[key] ?: return null
        if (entry.expiresAtMs > nowMs()) return entry.result
        entries.remove(key, entry)
        return null
    }

    fun remember(key: String, result: ExtractionResult<*>): Unit {
        val failure = result.deterministicFailure() ?: return
        val now = nowMs()
        if (entries.size >= maxEntries) entries.entries.removeIf { it.value.expiresAtMs <= now }
        if (entries.size >= maxEntries) return
        entries[key] = Entry(failure, now + ttlMs)
    }

    private fun ExtractionResult<*>.deterministicFailure(): ExtractionResult<Nothing>? = when (this) {
        is ExtractionResult.Success -> null
        is ExtractionResult.Failure -> takeIf { kind in DETERMINISTIC_KINDS }
        is ExtractionResult.BadRequest -> takeIf { code in DETERMINISTIC_CODES }
    }

    private data class Entry(val result: ExtractionResult<Nothing>, val expiresAtMs: Long)

    private companion object {
        const val DEFAULT_TTL_MS = 20_000L
        const val DEFAULT_MAX_ENTRIES = 4_096
        val DETERMINISTIC_KINDS = setOf(
            ExtractionFailureKind.ProviderAccessBlocked,
            ExtractionFailureKind.ContentUnavailable,
            ExtractionFailureKind.Unavailable,
        )
        val DETERMINISTIC_CODES = setOf(
            "age_restricted",
            "geographic_restriction",
            "members_only",
            "paid_content",
            "private_content",
        )
    }
}
