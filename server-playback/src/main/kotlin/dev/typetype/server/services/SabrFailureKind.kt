package dev.typetype.server.services

enum class SabrFailureKind {
    UPSTREAM_REFUSED,
    PROVIDER_SESSION_CHANGED,
    UPSTREAM_TIMEOUT,
    CONTENT_UNAVAILABLE,
    UNKNOWN,
}
