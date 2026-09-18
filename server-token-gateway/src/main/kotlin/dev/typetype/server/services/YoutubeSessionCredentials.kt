package dev.typetype.server.services

data class YoutubeSessionCredentials(
    val userId: String,
    val fingerprint: String,
    val cookies: String,
    val poToken: String,
    val authUser: Int = 0,
)
