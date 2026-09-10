package dev.typetype.server.routes

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.BlockedService
import dev.typetype.server.services.PublicHlsManifestTokenService
import dev.typetype.server.services.ProviderMediaHandleService

internal data class StreamRouteDependencies(
    val authService: AuthService?,
    val accessControlService: AccessControlService?,
    val adminSettingsService: AdminSettingsService?,
    val blockedService: BlockedService?,
    val publicHlsManifestTokenService: PublicHlsManifestTokenService?,
    val providerMediaHandleService: ProviderMediaHandleService?,
    val sabrStreamContractFilter: (suspend (String, StreamResponse) -> StreamResponse)?,
    val youtubeSessionSabrStreamInfo: (suspend (String, String) -> ExtractionResult<StreamResponse>?)?,
)
