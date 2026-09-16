package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class PresenceKeyCreateRequest(
    val name: String? = null,
)

@Serializable
data class PresenceKeyItem(
    val id: String,
    val name: String,
    val tokenPrefix: String,
    val scope: String = PresenceScopes.READ,
    val createdAt: Long,
    val lastUsedAt: Long? = null,
)

@Serializable
data class PresenceKeyCreatedResponse(
    val key: PresenceKeyItem,
    val token: String,
)

@Serializable
data class PresenceResponse(
    val active: Boolean,
    val nowPlaying: ActiveSessionNowPlayingItem? = null,
    val retryAfterMs: Long = 15_000L,
    val serverTimeMs: Long,
)

object PresenceScopes {
    const val READ = "presence:read"
    const val PRESENCE_RETRY_MS = 15_000L
}
