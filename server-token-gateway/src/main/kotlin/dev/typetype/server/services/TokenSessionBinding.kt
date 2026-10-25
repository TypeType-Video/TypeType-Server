package dev.typetype.server.services

data class TokenSessionBinding(
    val cookies: String,
    val poToken: String,
    val authUser: Int,
)
