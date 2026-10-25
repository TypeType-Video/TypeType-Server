package dev.typetype.server.services

sealed interface CacheAdmission {
    data class Valid(val ttlSeconds: Long) : CacheAdmission

    data class Suspect(val ttlSeconds: Long = MAX_SUSPECT_TTL_SECONDS) : CacheAdmission {
        val boundedTtlSeconds: Long get() = ttlSeconds.coerceIn(1L, MAX_SUSPECT_TTL_SECONDS)
    }

    data class Reject(val reason: String) : CacheAdmission

    companion object {
        const val MAX_SUSPECT_TTL_SECONDS = 30L
    }
}
