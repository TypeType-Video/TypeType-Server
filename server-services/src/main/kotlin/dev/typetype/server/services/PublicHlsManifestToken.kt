package dev.typetype.server.services

data class PublicHlsManifestToken(
    val manifestUrl: String,
    val expiresAt: Long,
)

sealed interface PublicHlsManifestTokenResult {
    data class Valid(val token: PublicHlsManifestToken) : PublicHlsManifestTokenResult
    data object Invalid : PublicHlsManifestTokenResult
    data object Expired : PublicHlsManifestTokenResult
}
