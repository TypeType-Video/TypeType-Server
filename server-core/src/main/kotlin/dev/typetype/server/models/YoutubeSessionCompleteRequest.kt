package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class YoutubeSessionCompleteRequest(
    val code: String,
    val cookies: String,
    val poToken: String,
    val authUser: Int = 0,
)
