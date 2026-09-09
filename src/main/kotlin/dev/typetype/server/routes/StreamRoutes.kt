package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.BlockedContentProfile
import dev.typetype.server.services.BlockedService
import dev.typetype.server.services.PublicHlsManifestTokenService
import dev.typetype.server.services.ProviderMediaHandleService
import dev.typetype.server.services.ProviderMediaType
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.YOUTUBE_SESSION_REQUIRED_CODE
import dev.typetype.server.services.YOUTUBE_SESSION_REQUIRED_ERROR
import dev.typetype.server.services.filterAllowed
import dev.typetype.server.services.filterBlocked
import dev.typetype.server.services.requiresYoutubeSession
import dev.typetype.server.services.withSabrManifestUrls
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.CancellationException

private const val STREAMS_CACHE_CONTROL = "public, max-age=21600, stale-while-revalidate=3600"
private const val AUTHENTICATED_STREAMS_CACHE_CONTROL = "no-store"
private const val PROVIDER_STREAMS_CACHE_CONTROL = "no-store"

fun Route.streamRoutes(
    streamService: StreamService,
    authService: AuthService? = null,
    accessControlService: AccessControlService? = null,
    adminSettingsService: AdminSettingsService? = null,
    blockedService: BlockedService? = null,
    publicHlsManifestTokenService: PublicHlsManifestTokenService? = null,
    providerMediaHandleService: ProviderMediaHandleService? = null,
    nicoNicoStreamService: StreamService = streamService,
    bilibiliStreamService: StreamService = streamService,
    sabrBootstrapStreamService: StreamService = streamService,
    youtubeSessionSabrStreamInfo: (suspend (String, String) -> ExtractionResult<StreamResponse>?)? = null,
    sabrStreamContractFilter: (suspend (String, StreamResponse) -> StreamResponse)? = null,
) {
    val dependencies = StreamRouteDependencies(
        authService = authService,
        accessControlService = accessControlService,
        adminSettingsService = adminSettingsService,
        blockedService = blockedService,
        publicHlsManifestTokenService = publicHlsManifestTokenService,
        providerMediaHandleService = providerMediaHandleService,
        sabrStreamContractFilter = sabrStreamContractFilter,
        youtubeSessionSabrStreamInfo = youtubeSessionSabrStreamInfo,
    )
    streamRoute("/streams/youtube/sabr", StreamDeliveryMode.YoutubeSabr, streamService, dependencies)
    streamRoute(
        "/streams/youtube/sabr/bootstrap",
        StreamDeliveryMode.YoutubeSabr,
        sabrBootstrapStreamService,
        dependencies,
    )
    streamRoute("/streams/niconico", StreamDeliveryMode.NicoNico, nicoNicoStreamService, dependencies)
    streamRoute("/streams/bilibili", StreamDeliveryMode.BiliBili, bilibiliStreamService, dependencies)
}

private fun Route.streamRoute(
    path: String,
    deliveryMode: StreamDeliveryMode,
    streamService: StreamService,
    dependencies: StreamRouteDependencies,
) {
    get(path) {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))
        if (!deliveryMode.accepts(url)) {
            return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("URL does not match stream endpoint", "provider_mismatch"),
            )
        }
        val access = call.accessProfileOrRespond(
            dependencies.authService,
            dependencies.accessControlService,
            dependencies.adminSettingsService,
        ) ?: return@get
        val accessProfile = access.profile
        val blockedProfile = access.userId
            ?.let { dependencies.blockedService?.profileFor(it) }
            ?: BlockedContentProfile.empty
        if (blockedProfile.blocksVideo(url)) {
            return@get call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse("Video is blocked", "content_blocked"),
            )
        }
        val publicResult = streamService.getStreamInfo(url)
        val resolution = resolveStreamInfo(url, deliveryMode, access.userId, publicResult, dependencies)
        when (val result = resolution.result) {
            is ExtractionResult.Success -> {
                if (!accessProfile.allowsUploader(result.data.uploaderUrl, result.data.uploaderName)) {
                    return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Channel is not allowed"))
                }
                if (!blockedProfile.allowsRequestedVideo(url, result.data.uploaderUrl, result.data.uploaderName)) {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("Channel is blocked", "content_blocked"),
                    )
                }
                val selected = if (deliveryMode.isSabr()) {
                    result.data.withSabrManifestUrls().onlySabrStreams()
                } else {
                    result.data.withoutSabrStreams()
                }
                val filtered = selected
                    .filterAllowed(accessProfile)
                    .filterBlocked(blockedProfile)
                    .withSignedPublicHlsUrl(
                        deliveryMode.isSabr() && selected.isLive || access.userId != null && !access.allowGuest,
                        dependencies.publicHlsManifestTokenService,
                    )
                val data = if (
                    !deliveryMode.isSabr() ||
                    resolution.authenticated ||
                    dependencies.sabrStreamContractFilter == null
                ) {
                    filtered
                } else {
                    dependencies.sabrStreamContractFilter.invoke(url, filtered)
                }
                if (!data.hasPlayableSource()) {
                    return@get call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ErrorResponse("No playable streams available", "no_playable_streams"),
                    )
                }
                val publicData = try {
                    dependencies.providerMediaHandleService?.let { service ->
                        providerMediaType(deliveryMode)?.let { service.materialize(data, it) }
                    } ?: data
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    return@get call.respond(
                        HttpStatusCode.BadGateway,
                        ErrorResponse(
                            error.message ?: "Provider media handle service failed",
                            "media_handle_unavailable",
                        ),
                    )
                }
                call.response.headers.append(
                    HttpHeaders.CacheControl,
                    when {
                        access.userId != null -> AUTHENTICATED_STREAMS_CACHE_CONTROL
                        deliveryMode == StreamDeliveryMode.NicoNico ||
                            deliveryMode == StreamDeliveryMode.BiliBili -> PROVIDER_STREAMS_CACHE_CONTROL
                        else -> STREAMS_CACHE_CONTROL
                    },
                )
                call.respond(publicData)
            }
            is ExtractionResult.BadRequest ->
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message, result.code))
            is ExtractionResult.Failure ->
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message, result.code))
        }
    }
}

private fun providerMediaType(deliveryMode: StreamDeliveryMode): ProviderMediaType? = when (deliveryMode) {
    StreamDeliveryMode.NicoNico -> ProviderMediaType.NICONICO
    StreamDeliveryMode.BiliBili -> ProviderMediaType.BILIBILI
    StreamDeliveryMode.YoutubeSabr -> null
}

private data class StreamResolution(
    val result: ExtractionResult<StreamResponse>,
    val authenticated: Boolean = false,
)

private suspend fun resolveStreamInfo(
    url: String,
    deliveryMode: StreamDeliveryMode,
    userId: String?,
    publicResult: ExtractionResult<StreamResponse>,
    dependencies: StreamRouteDependencies,
): StreamResolution {
    val authenticatedInfo = dependencies.youtubeSessionSabrStreamInfo
    if (!deliveryMode.isSabr() || authenticatedInfo == null) {
        return StreamResolution(publicResult)
    }
    val authenticatedResult = userId?.let { authenticatedInfo(it, url) }
    if (authenticatedResult != null) return StreamResolution(authenticatedResult, authenticated = true)
    return if (publicResult.requiresYoutubeSession()) {
        StreamResolution(
            ExtractionResult.BadRequest(YOUTUBE_SESSION_REQUIRED_ERROR, YOUTUBE_SESSION_REQUIRED_CODE),
        )
    } else {
        StreamResolution(publicResult)
    }
}

private fun StreamResponse.hasPlayableSource(): Boolean =
    videoStreams.isNotEmpty() ||
        videoOnlyStreams.isNotEmpty() ||
        audioStreams.isNotEmpty() ||
        hlsUrl.isNotBlank() ||
        dashMpdUrl.isNotBlank()

private fun StreamResponse.withSignedPublicHlsUrl(
    shouldSign: Boolean,
    tokenService: PublicHlsManifestTokenService?,
): StreamResponse {
    if (!shouldSign || tokenService == null || hlsUrl.isBlank()) return this
    if (hlsUrl.startsWith("/streams/hls-manifest?token=")) return this
    return copy(hlsUrl = tokenService.createPath(hlsUrl))
}
