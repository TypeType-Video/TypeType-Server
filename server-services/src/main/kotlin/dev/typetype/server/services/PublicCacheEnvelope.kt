package dev.typetype.server.services

import kotlinx.serialization.Serializable

@Serializable
data class PublicCacheEnvelope<T>(val value: T, val storedAt: Long)
