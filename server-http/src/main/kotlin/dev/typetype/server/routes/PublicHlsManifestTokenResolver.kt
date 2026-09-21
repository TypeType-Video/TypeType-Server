package dev.typetype.server.routes

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.HlsManifestService
import dev.typetype.server.services.PublicHlsManifestTokenResult
import dev.typetype.server.services.PublicHlsManifestTokenService

internal suspend fun PublicHlsManifestTokenService.resolvePublicHlsManifest(
    token: String,
    hlsManifestService: HlsManifestService,
): ExtractionResult<String>? = when (val verified = verify(token)) {
    is PublicHlsManifestTokenResult.Valid ->
        hlsManifestService.hlsManifest(verified.token.manifestUrl, signManifestLinks = true)
    PublicHlsManifestTokenResult.Expired ->
        ExtractionResult.BadRequest("Signed HLS manifest token expired")
    PublicHlsManifestTokenResult.Invalid -> null
}
