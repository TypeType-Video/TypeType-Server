package dev.typetype.server.routes

import dev.typetype.server.PlaybackTraceLog
import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.VideoItem
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
import kotlinx.coroutines.withContext

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
    youtubeRelatedVideos: (suspend (String) -> ExtractionResult<List<VideoItem>>)? = null,
    previewsEnabled: Boolean = StreamPreview.enabledByDefault,
    sabrStreamContractFilter: (suspend (String, StreamResponse) -> StreamResponse?)? = null,
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
        youtubeRelatedVideos = youtubeRelatedVideos,
    )
    val bootstrapDependencies = dependencies.copy(youtubeRelatedVideos = null)
    streamRoute("/streams/youtube/sabr", StreamDeliveryMode.YoutubeSabr, streamService, dependencies, previewsEnabled)
    streamRoute("/streams/youtube/live", StreamDeliveryMode.YoutubeLiveHls, youtubeLiveHlsStreamService, dependencies, previewsEnabled)
    streamRoute(
        "/streams/youtube/sabr/bootstrap",
        StreamDeliveryMode.YoutubeSabr,
        sabrBootstrapStreamService,
        bootstrapDependencies,
        previewsEnabled,
    )
    streamRoute("/streams/niconico", StreamDeliveryMode.NicoNico, nicoNicoStreamService, dependencies, previewsEnabled)
    streamRoute("/streams/bilibili", StreamDeliveryMode.BiliBili, bilibiliStreamService, dependencies, previewsEnabled)
}

private fun Route.streamRoute(
    path: String,
    deliveryMode: StreamDeliveryMode,
    streamService: StreamService,
    dependencies: StreamRouteDependencies,
    previewsEnabled: Boolean,
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
        PlaybackTraceLog.record("stream_route_start", "deliveryMode=$deliveryMode")
        val accessStartedAt = System.nanoTime()
        val access = call.accessProfileOrRespond(
            dependencies.authService,
            dependencies.accessControlService,
            dependencies.adminSettingsService,
        ) ?: return@get
        val accessProfile = access.profile
        val blockedProfile = access.userId
            ?.let { dependencies.blockedService?.profileFor(it) }
            ?: BlockedContentProfile.empty
        PlaybackTraceLog.record(
            "stream_route_access",
            "durationMs=${(System.nanoTime() - accessStartedAt) / 1_000_000} guest=${access.userId == null}",
        )
        if (blockedProfile.blocksVideo(url)) {
            return@get call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse("Video is blocked", "content_blocked"),
            )
        }
        val providerStartedAt = System.nanoTime()
        val preview = StreamPreview(call.request.queryParameters["intent"], previewsEnabled)
        preview.switchedOff()?.let { return@get call.respond(HttpStatusCode.Conflict, it) }
        val publicResult = withContext(preview.context) { streamService.getStreamInfo(url) }
        preview.notNow(publicResult)?.let { return@get call.respond(HttpStatusCode.Conflict, it) }
        val providerResult = when (publicResult) {
            is ExtractionResult.Success -> "success"
            is ExtractionResult.Failure -> "failure"
            is ExtractionResult.BadRequest -> "bad_request"
        }
        PlaybackTraceLog.record(
            "stream_provider_info",
            "durationMs=${(System.nanoTime() - providerStartedAt) / 1_000_000} result=$providerResult",
        )
        val resolutionStartedAt = System.nanoTime()
        val resolution = withContext(preview.context) { resolveStreamInfo(url, deliveryMode, access.userId, publicResult, dependencies) }
        val resolutionResult = when (resolution.result) {
            is ExtractionResult.Success -> "success"
            is ExtractionResult.Failure -> "failure"
            is ExtractionResult.BadRequest -> "bad_request"
        }
        PlaybackTraceLog.record(
            "stream_resolution",
            "durationMs=${(System.nanoTime() - resolutionStartedAt) / 1_000_000} result=$resolutionResult authenticated=${resolution.authenticated}",
        )
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
                        ?: return@get call.respondNoPlayableStreams(upstreamOutage = true)
                }
                if (!data.hasPlayableSource()) {
                    return@get call.respondNoPlayableStreams(upstreamOutage = false)
                }
                val publicData = try {
                    dependencies.providerMediaHandleService?.let { service ->
                        providerMediaType(deliveryMode)?.let { service.materialize(data, it) }
                    } ?: data
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    return@get call.respondMediaHandleFailure(error)
                }
                call.response.headers.append(
                    HttpHeaders.CacheControl,
                    streamCacheControl(deliveryMode, selected.isLive, access.userId),
                )
                call.respond(publicData)
            }
            is ExtractionResult.BadRequest -> call.respondExtractionBadRequest(result, deliveryMode.isYoutube())
            is ExtractionResult.Failure -> call.respondExtractionFailure(result, deliveryMode.isYoutube())
        }
    }
}
