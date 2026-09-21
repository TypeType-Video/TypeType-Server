package dev.typetype.server.services

data class AudioOnlyMediaToken(
    val userId: String?,
    val videoUrl: String,
    val preferOriginal: Boolean,
    val preferredLocale: String?,
    val selectedItag: Int,
    val selectedAudioTrackId: String?,
    val expiresAt: Long,
)

sealed interface AudioOnlyMediaTokenResult {
    data class Valid(val token: AudioOnlyMediaToken) : AudioOnlyMediaTokenResult
    data object Invalid : AudioOnlyMediaTokenResult
    data object Expired : AudioOnlyMediaTokenResult
}
