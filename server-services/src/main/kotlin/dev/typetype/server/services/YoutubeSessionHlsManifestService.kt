package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult

class YoutubeSessionHlsManifestService(
    private val youtubeSessionService: YoutubeSessionService,
    private val streamService: YoutubeSessionStreamService,
    private val hlsManifestService: HlsManifestService,
    private val tokenService: SignedHlsManifestTokenService,
) {
    suspend fun hlsManifest(token: String, expectedUrl: String? = null): ExtractionResult<String> =
        when (val verified = tokenService.verify(token)) {
            is SignedHlsManifestTokenResult.Valid -> {
                if (expectedUrl != null && verified.token.videoUrl != expectedUrl) {
                    ExtractionResult.BadRequest("Invalid signed HLS manifest token")
                } else {
                    hlsManifest(verified.token)
                }
            }
            SignedHlsManifestTokenResult.Expired ->
                ExtractionResult.BadRequest("Signed HLS manifest token expired")
            SignedHlsManifestTokenResult.Invalid ->
                ExtractionResult.BadRequest("Invalid signed HLS manifest token")
        }

    suspend fun hlsManifestForUser(userId: String, videoUrl: String): ExtractionResult<String> {
        val credentials = youtubeSessionService.connectedCredentials(userId)
            ?: return ExtractionResult.BadRequest(YOUTUBE_SESSION_RECONNECT_ERROR)
        return hlsManifestForCredentials(credentials, videoUrl)
    }

    private suspend fun hlsManifest(token: SignedHlsManifestToken): ExtractionResult<String> {
        val credentials = youtubeSessionService.connectedCredentials(token.userId)
            ?: return ExtractionResult.BadRequest(YOUTUBE_SESSION_RECONNECT_ERROR)
        if (credentials.fingerprint != token.fingerprint) {
            return ExtractionResult.BadRequest(YOUTUBE_SESSION_RECONNECT_ERROR)
        }
        return hlsManifestForCredentials(credentials, token.videoUrl)
    }

    private suspend fun hlsManifestForCredentials(
        credentials: YoutubeSessionCredentials,
        videoUrl: String,
    ): ExtractionResult<String> =
        hlsManifestService.hlsManifestFromStreamInfo(
            streamService.getStreamInfoForCredentials(credentials, videoUrl),
            signManifestLinks = true,
        )
}
