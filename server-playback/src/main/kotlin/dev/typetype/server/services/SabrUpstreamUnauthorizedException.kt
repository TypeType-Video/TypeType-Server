package dev.typetype.server.services

class SabrUpstreamUnauthorizedException(
    val status: Int,
    val providerSessionChanged: Boolean = false,
) : Exception(
    if (providerSessionChanged) {
        "SABR upstream unauthorized HTTP $status: provider session changed"
    } else {
        "SABR upstream unauthorized HTTP $status after TypeType token refresh"
    },
) {
    val kind: SabrFailureKind
        get() = if (providerSessionChanged) SabrFailureKind.PROVIDER_SESSION_CHANGED else SabrFailureKind.UPSTREAM_REFUSED
}
