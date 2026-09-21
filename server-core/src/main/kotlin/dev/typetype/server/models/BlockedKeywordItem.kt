package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class BlockedKeywordItem(
    val keyword: String,
    val blockedAt: Long = 0L,
    val global: Boolean? = null,
)
