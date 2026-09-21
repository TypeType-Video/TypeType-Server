package dev.typetype.server.services

data class YoutubeSessionCookie(
    val name: String,
    val value: String,
    val priority: Int,
)
