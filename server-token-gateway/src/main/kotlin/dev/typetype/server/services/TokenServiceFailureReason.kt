package dev.typetype.server.services

enum class TokenServiceFailureReason {
    HTTP_STATUS,
    TIMEOUT,
    NETWORK,
    INVALID_RESPONSE,
}
