package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthenticatedSabrInfoService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SabrPreparedInfo
import dev.typetype.server.services.SabrPlaybackSegmentResult
import dev.typetype.server.services.SabrPlaybackSessionService
import dev.typetype.server.services.SabrPlaybackInfoResolver
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.PlaybackTraceLog
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.markServed
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import dev.typetype.server.sabr.YoutubeSabrFormat

internal class SabrPlaybackHandler(
    private val sabrSessionStore: SabrSessionStore,
    private val streamService: StreamService,
    private val authService: AuthService?,
    private val accessControlService: AccessControlService?,
    private val adminSettingsService: AdminSettingsService?,
    authenticatedSabrInfoService: AuthenticatedSabrInfoService? = null,
    youtubeSessionStreamInfo: (suspend (String, String) -> ExtractionResult<StreamResponse>?)? = null,
) {
    private val playbackService = SabrPlaybackSessionService(sabrSessionStore)
    private val infoResolver = SabrPlaybackInfoResolver(sabrSessionStore, authenticatedSabrInfoService)
    private val accessValidator = SabrPlaybackAccessValidator(streamService, youtubeSessionStreamInfo)
    private val prewarmHandoffs = SabrPlaybackPrewarmHandoffs()

    suspend fun create(call: ApplicationCall, videoId: String) {
        val access = call.accessProfileOrRespond(authService, accessControlService, adminSettingsService) ?: return
        if (!validateAccess(call, videoId, access)) return
        val request = call.playbackRequest()
        val startTimeMs = request.effectiveStartTimeMs()
        val isPrewarm = call.request.queryParameters["prewarm"]?.toBooleanStrictOrNull() == true
        val handoffKey = prewarmHandoffs.key(access.userId, videoId, request, startTimeMs)
        if (!isPrewarm) {
            val handoff = handoffKey?.let(prewarmHandoffs::take)
            val existing = handoff?.let { playbackService.lookup(it.sessionToken) }
            if (existing != null && existing.terminalFailure() == null) {
                PlaybackTraceLog.record("sabr_prewarm_handoff", "result=hit videoItag=${existing.videoFormat.itag} audioItag=${existing.audioFormat.itag} audioTrackId=${existing.audioFormat.audioTrackId} startTimeMs=${handoff.startTimeMs} ready=${handoff.ready}")
                return respondPrepared(call, existing, videoId, handoff.startTimeMs, handoff.ready)
            }
        }
        val infoStartedAt = System.nanoTime()
        val prepared = infoResolver.initial(access.userId, videoId, startTimeMs)
        PlaybackTraceLog.record("sabr_info", "durationMs=${(System.nanoTime() - infoStartedAt) / 1_000_000} result=${if (prepared == null) "miss" else "ready"} startTimeMs=$startTimeMs")
        prepared ?: return call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("SABR probe failed"))
        val audio = selectAudio(call, prepared, request) ?: return
        val video = selectVideo(call, prepared, request) ?: return
        val prepareStartedAt = System.nanoTime()
        val preparation = playbackService.prepare(
            videoId = videoId,
            userId = access.userId ?: videoId,
            prepared = prepared,
            audio = audio,
            video = video,
            startTimeMs = startTimeMs,
            audioOnly = request.audioOnly,
            isLive = request.isLive,
        )
        PlaybackTraceLog.record("sabr_session_prepare", "durationMs=${(System.nanoTime() - prepareStartedAt) / 1_000_000} ready=${preparation.ready} videoItag=${video.itag} audioItag=${audio.itag} audioTrackId=${audio.audioTrackId} audioOnly=${request.audioOnly} isLive=${request.isLive} startTimeMs=${preparation.startTimeMs}")
        preparation.holder.setActiveTracks(videoActive = !request.audioOnly, audioActive = true)
        if (isPrewarm && handoffKey != null && preparation.holder.terminalFailure() == null) {
            prewarmHandoffs.remember(handoffKey, SabrPlaybackPrewarmSession(preparation.holder.sessionToken, preparation.startTimeMs, preparation.ready))
            PlaybackTraceLog.record("sabr_prewarm_handoff", "result=stored videoItag=${video.itag} audioItag=${audio.itag} audioTrackId=${audio.audioTrackId} startTimeMs=$startTimeMs")
        }
        respondPrepared(call, preparation.holder, videoId, preparation.startTimeMs, preparation.ready)
    }

    suspend fun seek(call: ApplicationCall, sessionId: String) {
        val holder = playbackService.lookup(sessionId)
            ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("No active SABR playback session"))
        val request = call.playbackRequest()
        val playerTimeMs = request.effectiveStartTimeMs()
        if (request.keepsCurrentFormats(holder)) {
            val preparation = playbackService.seekExisting(holder, playerTimeMs, request.audioOnly)
            return respondPrepared(call, holder, holder.key.videoId, preparation.startTimeMs, preparation.ready)
        }
        val prepared = infoResolver.replacement(holder, playerTimeMs)
            ?: return call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("SABR probe failed"))
        val audio = SabrFormatSelector.audio(
            prepared.info,
            request.audioItag ?: holder.audioFormat.itag,
            request.audioTrackId ?: holder.audioFormat.audioTrackId,
            requireAac = false,
        ) ?: return call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("No SABR audio for this video"))
        val video = SabrFormatSelector.video(prepared.info, request.videoItag ?: holder.videoFormat.itag)
            ?: return call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("No SABR video for this video"))
        val preparation = playbackService.seek(
            source = holder,
            prepared = prepared,
            audio = audio,
            video = video,
            playerTimeMs = playerTimeMs,
            audioOnly = request.audioOnly,
        )
        preparation.holder.setActiveTracks(videoActive = !request.audioOnly, audioActive = true)
        respondPrepared(call, preparation.holder, preparation.holder.key.videoId, preparation.startTimeMs, preparation.ready)
    }

    suspend fun manifest(call: ApplicationCall, sessionId: String) {
        val holder = playbackService.lookup(sessionId)
            ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("No active SABR playback session"))
        playbackService.startPump(holder)
        playbackService.warmPlayback(holder)
        call.respondSabrPlaybackManifest(holder)
    }

    suspend fun segment(call: ApplicationCall, sessionId: String, isInit: Boolean, seq: Int) {
        val holder = playbackService.lookup(sessionId)
            ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("No active SABR playback session"))
        val itag = call.parameters["itag"]?.toIntOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid itag"))
        val format = holder.formatForItag(itag)
            ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("No active SABR track for this request"))
        val generation = call.request.queryParameters["generation"]?.toLongOrNull() ?: holder.activeGeneration()
        val result = if (isInit) {
            playbackService.fetchInitialization(holder, format, PLAYBACK_SEGMENT_TIMEOUT_MS, generation)
        } else {
            playbackService.fetchMedia(holder, format, seq, PLAYBACK_SEGMENT_TIMEOUT_MS, generation)
        }
        respondSegment(call, result)
    }

    private suspend fun respondPrepared(call: ApplicationCall, holder: SabrSessionHolder, videoId: String, startTimeMs: Long, ready: Boolean) {
        val response = holder.toPlaybackResponse(videoId, startTimeMs, ready, RETRY_AFTER_MS)
        call.respond(if (ready) HttpStatusCode.OK else HttpStatusCode.Accepted, response)
    }

    private suspend fun respondSegment(call: ApplicationCall, result: SabrPlaybackSegmentResult): Unit = when (result) {
        is SabrPlaybackSegmentResult.Ready -> call.respondSabrMediaBytes(result.mimeType, result.bytes)
        is SabrPlaybackSegmentResult.Stream -> call.respondSabrMediaStream(
            result.mimeType,
            result.segment.length.toLong(),
            result.segment::openStream,
        ) { result.holder.markServed(result.segment, result.generation) }
        is SabrPlaybackSegmentResult.Retry -> call.respond(
            HttpStatusCode.Accepted,
            result.holder.toRetryPlaybackResponse(result.status, RETRY_AFTER_MS),
        )
        is SabrPlaybackSegmentResult.Stale -> call.respond(HttpStatusCode.Conflict, ErrorResponse("Stale SABR playback generation"))
        SabrPlaybackSegmentResult.InvalidSequence -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid seq"))
        SabrPlaybackSegmentResult.InvalidGeneration -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid generation"))
    }

    private suspend fun validateAccess(call: ApplicationCall, videoId: String, access: AccessRouteProfile): Boolean {
        if (!access.profile.enabled) return true
        return when (val result = accessValidator.resolve(access.userId, videoId)) {
            is ExtractionResult.Success -> {
                val allowed = !access.profile.enabled ||
                    access.profile.allowsUploader(result.data.uploaderUrl, result.data.uploaderName)
                if (allowed) true else {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("Channel is not allowed"))
                    false
                }
            }
            is ExtractionResult.Failure -> {
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message, result.code))
                false
            }
            is ExtractionResult.BadRequest -> {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message, result.code))
                false
            }
        }
    }


    private suspend fun selectAudio(
        call: ApplicationCall,
        prepared: SabrPreparedInfo,
        request: SabrPlaybackRequest,
    ): YoutubeSabrFormat? = SabrFormatSelector.audio(
        prepared.info,
        request.audioItag,
        request.audioTrackId,
        requireAac = false,
    ) ?: run {
        call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("No SABR audio for this video"))
        null
    }

    private suspend fun selectVideo(
        call: ApplicationCall,
        prepared: SabrPreparedInfo,
        request: SabrPlaybackRequest,
    ): YoutubeSabrFormat? = SabrFormatSelector.video(prepared.info, request.videoItag) ?: run {
        call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("No SABR video for this video"))
        null
    }

    private fun SabrSessionHolder.formatForItag(itag: Int): YoutubeSabrFormat? = when (itag) {
        audioFormat.itag -> audioFormat
        videoFormat.itag -> videoFormat
        else -> null
    }

    private companion object {
        const val PLAYBACK_SEGMENT_TIMEOUT_MS = 4_000L
        const val RETRY_AFTER_MS = 250L
    }
}
