package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class SearchHistoryItem(
    val id: String = "",
    val term: String,
    val searchedAt: Long = 0L,
)
