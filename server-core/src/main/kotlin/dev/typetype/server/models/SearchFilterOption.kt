package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class SearchFilterOption(
    val value: String,
    val label: String,
    val isDefault: Boolean = false,
)
