package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class SearchFilterGroup(
    val key: String,
    val label: String,
    val multiSelect: Boolean,
    val options: List<SearchFilterOption>,
)
