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
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.filterAllowed
import dev.typetype.server.services.filterBlocked
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.CancellationException


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
    youtubeLiveHlsStreamService: StreamService = streamService,
    youtubeSessionSabrStreamInfo: (suspend (String, String) -> ExtractionResult<StreamResponse>?)? = null,
    bilibiliSessionStreamInfo: (suspend (String, String) -> ExtractionResult<StreamResponse>?)? = null,
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
        bilibiliSessionStreamInfo = bilibiliSessionStreamInfo,
    )
    streamRoute("/streams/youtube/sabr", StreamDeliveryMode.YoutubeSabr, streamService, dependencies)
    streamRoute("/streams/youtube/live", StreamDeliveryMode.YoutubeLiveHls, youtubeLiveHlsStreamService, dependencies)
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
                    result.data.forSabrPlayback()
                } else if (deliveryMode == StreamDeliveryMode.YoutubeLiveHls) {
                    result.data.onlyLiveHls()
                } else {
                    result.data.withoutSabrStreams()
                }
                val filtered = selected
                    .filterAllowed(accessProfile)
                    .filterBlocked(blockedProfile)
                    .withSignedPublicHlsUrl(
                        deliveryMode.isSabr() && selected.isLive ||
                            deliveryMode == StreamDeliveryMode.YoutubeLiveHls && selected.isLive ||
                            access.userId != null && !access.allowGuest,
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
                        ErrorResponse(
                            "No compatible stream is available for this video",
                            "no_playable_streams",
                        ),
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
                    streamCacheControl(deliveryMode, selected.isLive, access.userId),
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
