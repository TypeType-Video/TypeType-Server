package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class ChannelPageRequest(
    val url: String? = null,
    val nextpage: String? = null,
    val sort: String? = null,
)
