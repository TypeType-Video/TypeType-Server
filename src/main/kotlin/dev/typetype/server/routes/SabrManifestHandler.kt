package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AudioOnlyMediaTokenService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SabrManifestBuilder
import dev.typetype.server.services.SabrPreparedInfo
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionPurpose
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.bothFormatsKnown
import dev.typetype.server.sabr.YoutubeSabrFormat
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import kotlinx.coroutines.withTimeoutOrNull

internal class SabrManifestHandler(
    private val sabrSessionStore: SabrSessionStore,
    private val streamService: StreamService,
    private val authService: AuthService?,
    private val accessControlService: AccessControlService?,
    private val adminSettingsService: AdminSettingsService?,
    private val audioOnlyTokenService: AudioOnlyMediaTokenService?,
) {
    private val accessResolver = SabrManifestAccessResolver(audioOnlyTokenService)
    private val responder = SabrManifestResponder(sabrSessionStore)

    suspend fun handle(call: ApplicationCall, videoId: String, download: Boolean = false) {
        val audioOnly = call.request.queryParameters["audioOnly"].equals("true", ignoreCase = true)
        val downloadRange = if (download) {
            call.sabrDownloadRange().getOrElse {
                return call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Invalid download range"))
            }
        } else {
            null
        }
        val hls = call.request.queryParameters["format"].equals("hls", ignoreCase = true)
        val playlist = call.request.queryParameters["playlist"]
        val sessionToken = call.request.queryParameters["session"]
        if (hls && playlist != null && call.request.queryParameters["session"] != null) {
            return handleHlsPlaylist(call, videoId, playlist)
        }
        if (sessionToken != null) {
            if (download) {
                return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Download streams require a new SABR session"))
            }
            val holder = sabrSessionStore.lookupByToken(videoId, sessionToken)
                ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("No active SABR session for this request"))
            return responder.respond(call, holder, videoId, audioOnly, hls, download = false)
        }
        val manifestAccess = accessResolver.resolve(call, videoId) ?: return
        val access = when (manifestAccess) {
            is SabrManifestAccess.AudioOnlyToken -> null
            SabrManifestAccess.RequiresAuth ->
                call.accessProfileOrRespond(authService, accessControlService, adminSettingsService) ?: return
        }
        val url = "https://www.youtube.com/watch?v=$videoId"
        if (access?.profile?.enabled == true) {
            when (val result = streamService.getStreamInfo(url)) {
                is ExtractionResult.Success -> {
                    if (!access.profile.allowsUploader(result.data.uploaderUrl, result.data.uploaderName)) {
                        return call.respond(HttpStatusCode.Forbidden, ErrorResponse("Channel is not allowed"))
                    }
                }
                is ExtractionResult.Failure ->
                    return call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
                is ExtractionResult.BadRequest ->
                    return call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            }
        }
        val requestedStartTimeMs = call.request.queryParameters["playerTimeMs"]?.toLongOrNull()?.coerceAtLeast(0L)
            ?: call.request.queryParameters["startTimeMs"]?.toLongOrNull()?.coerceAtLeast(0L)
            ?: 0L
        val prepared = sabrSessionStore.fetchInfo(
            videoId,
            if (downloadRange == null) requestedStartTimeMs else 0L,
            cachedFirst = !download,
            isolatedPlayback = download,
        )
            ?: return call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("SABR probe failed"))
        val audioToken = (manifestAccess as? SabrManifestAccess.AudioOnlyToken)?.token
        val audio = SabrFormatSelector.audio(
            prepared.info,
            audioToken?.selectedItag ?: call.request.queryParameters["audioItag"]?.toIntOrNull(),
            audioToken?.selectedAudioTrackId ?: call.request.queryParameters["audioTrackId"],
            requireAac = true,
        )
            ?: return call.respond(
                HttpStatusCode.UnprocessableEntity,
                ErrorResponse("No SABR audio for this video"),
            )
        val video = SabrFormatSelector.video(
            prepared.info,
            call.request.queryParameters["videoItag"]?.toIntOrNull(),
        )
            ?: return call.respond(
                HttpStatusCode.UnprocessableEntity,
                ErrorResponse("No SABR video for this video"),
            )
        val startTimeMs = downloadRange?.startTimeMs(audio, video, audioOnly) ?: requestedStartTimeMs
        val userId = audioToken?.userId ?: access?.userId ?: videoId
        val purpose = if (download || call.request.queryParameters["workload"] == "download") {
            SabrSessionPurpose.DOWNLOAD
        } else {
            SabrSessionPurpose.MANIFEST
        }
        val holder = createHolder(videoId, userId, prepared, audio, video, startTimeMs, purpose, audioOnly)
        if (download) {
            holder.setActiveTracks(videoActive = !audioOnly, audioActive = true)
            return responder.respond(
                call,
                holder,
                videoId,
                audioOnly,
                hls = false,
                download = true,
                downloadRange = downloadRange,
            )
        }
        if (audioOnly) {
            holder.setActiveTracks(videoActive = false, audioActive = true)
            sabrSessionStore.ensureWarmed(holder)
        } else if (!bothFormatsKnown(holder)) {
            val readyHolder = preflightOrRecreate(videoId, userId, prepared, audio, video, startTimeMs, purpose, holder)
            if (readyHolder == null) {
                return call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("SABR preflight failed"))
            }
            return responder.respond(call, readyHolder, videoId, audioOnly, hls, download = false)
        }
        responder.respond(call, holder, videoId, audioOnly, hls, download = false)
    }

    private suspend fun preflightOrRecreate(
        videoId: String,
        userId: String,
        prepared: SabrPreparedInfo,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        startTimeMs: Long,
        purpose: SabrSessionPurpose,
        holder: SabrSessionHolder,
    ): SabrSessionHolder? {
        if (preflight(holder, startTimeMs)) return holder
        sabrSessionStore.release(holder)
        val refreshed = sabrSessionStore.fetchInfo(videoId, startTimeMs, cachedFirst = false) ?: prepared
        val refreshedAudio = SabrFormatSelector.audio(refreshed.info, audio.itag, audio.audioTrackId, requireAac = true)
            ?: return null
        val refreshedVideo = SabrFormatSelector.video(refreshed.info, video.itag) ?: return null
        val fresh = createHolder(videoId, userId, refreshed, refreshedAudio, refreshedVideo, startTimeMs, purpose)
        return fresh.takeIf { preflight(it, startTimeMs) }
    }

    private suspend fun preflight(holder: SabrSessionHolder, startTimeMs: Long): Boolean =
        withTimeoutOrNull(PREFLIGHT_TIMEOUT_MS) { sabrSessionStore.preflightPlayback(holder, startTimeMs) } == true

    private fun createHolder(
        videoId: String,
        userId: String,
        prepared: SabrPreparedInfo,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        startTimeMs: Long,
        purpose: SabrSessionPurpose,
        audioOnly: Boolean = false,
    ): SabrSessionHolder = sabrSessionStore.getOrCreate(
        videoId,
        userId,
        prepared.info,
        audio,
        video,
        prepared.initialToken,
        startTimeMs,
        startPump = false,
        purpose = purpose,
        audioOnly = audioOnly,
    )

    private suspend fun handleHlsPlaylist(call: ApplicationCall, videoId: String, playlist: String) {
        val holder = sabrSessionStore.lookupByToken(videoId, call.request.queryParameters["session"].orEmpty())
            ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("No active SABR session for this request"))
        sabrSessionStore.ensureWarmed(holder)
        val state = holder.session.streamState
        val manifest = when (playlist) {
            "audio" -> SabrManifestBuilder.buildAudioOnlyHls(
                videoId,
                holder.audioFormat,
                state.getEndSegment(holder.audioFormat),
                state,
                holder.sessionToken,
            )
            "video" -> SabrManifestBuilder.buildVideoOnlyHls(
                videoId,
                holder.videoFormat,
                state.getEndSegment(holder.videoFormat),
                state,
                holder.sessionToken,
            )
            else -> return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid HLS playlist"))
        }
        call.response.headers.append("Cache-Control", "no-store")
        call.respondText(manifest, HLS_CONTENT_TYPE)
    }

    private companion object {
        const val PREFLIGHT_TIMEOUT_MS = 25_000L
    }
}
