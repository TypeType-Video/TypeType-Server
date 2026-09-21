package dev.typetype.server.services

object AuthenticatedSabrPolicy {
    const val INFO_TIMEOUT_MS = 15_000L
    const val STREAM_TIMEOUT_MS = 20_000L
    const val TIMEOUT_CODE = "authenticated_sabr_timeout"
}
