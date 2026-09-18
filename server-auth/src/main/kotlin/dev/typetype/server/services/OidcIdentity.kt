package dev.typetype.server.services

data class OidcIdentity(
    val issuer: String,
    val subject: String,
    val email: String,
    val name: String,
)
