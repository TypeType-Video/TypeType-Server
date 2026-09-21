package dev.typetype.server.services

data class SignedHlsManifestToken(
    val userId: String,
    val videoUrl: String,
    val fingerprint: String,
    val expiresAt: Long,
)

sealed interface SignedHlsManifestTokenResult {
    data class Valid(val token: SignedHlsManifestToken) : SignedHlsManifestTokenResult
    data object Invalid : SignedHlsManifestTokenResult
    data object Expired : SignedHlsManifestTokenResult
}
