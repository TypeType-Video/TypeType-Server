package dev.typetype.server.routes

import dev.typetype.server.services.SabrInitializationData
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.livePlaybackSnapshot
import dev.typetype.server.services.resolvePlaybackStartMs

internal class SabrPlaybackWindowBuilder(private val sabrSessionStore: SabrSessionStore) {
    private val trackBuilder = SabrPlaybackWindowTrackBuilder(sabrSessionStore)

    suspend fun build(
        holder: SabrSessionHolder,
        request: SabrPlaybackWindowRequest,
    ): SabrPlaybackWindowBuildResult {
        val startTimeMs = holder.resolvePlaybackStartMs(request.playerTimeMs)
        val effectiveRequest = if (startTimeMs == request.playerTimeMs) request else request.copy(playerTimeMs = startTimeMs)
        val live = holder.livePlaybackSnapshot()
        SabrInitializationData.ingestRemembered(holder.audioFormat, holder)
        if (!effectiveRequest.audioOnly) SabrInitializationData.ingestRemembered(holder.videoFormat, holder)
        if (effectiveRequest.audioOnly) return buildAudioOnly(holder, effectiveRequest, live?.toResponse())
        val activeLive = live?.active == true
        var video = trackBuilder.buildTrack(holder, holder.videoFormat, effectiveRequest, effectiveRequest.playerTimeMs, activeLive)
        val decodeStartMs = video.track.segments.firstOrNull()?.startMs ?: effectiveRequest.playerTimeMs
        val audioStartMs = if (activeLive) minOf(effectiveRequest.playerTimeMs, decodeStartMs) else effectiveRequest.playerTimeMs
        var audio = trackBuilder.buildTrack(
            holder,
            holder.audioFormat,
            effectiveRequest,
            audioStartMs,
            activeLive,
        )
        var playbackStartMs = resolvedPlaybackStartMs(
            effectiveRequest.playerTimeMs,
            activeLive,
            video.track,
            audio.track,
        )
        val readyAheadMs = readyAheadMs(effectiveRequest, activeLive)
        var requestedReadyEndMs = playbackStartMs + readyAheadMs
        if (activeLive) {
            if (!video.covers(requestedReadyEndMs) && video.blockedRequest == null) {
                video = trackBuilder.buildTrack(
                    holder, holder.videoFormat, effectiveRequest, effectiveRequest.playerTimeMs, activeLive,
                    requestedReadyEndMs,
                )
            }
            if (!audio.covers(requestedReadyEndMs) && audio.blockedRequest == null) {
                audio = trackBuilder.buildTrack(
                    holder, holder.audioFormat, effectiveRequest, audioStartMs, activeLive, requestedReadyEndMs,
                )
            }
            playbackStartMs = resolvedPlaybackStartMs(
                effectiveRequest.playerTimeMs, activeLive, video.track, audio.track,
            )
            requestedReadyEndMs = playbackStartMs + readyAheadMs
        }
        val blocked = blockedTrack(audio, video)
        return SabrPlaybackWindowBuildResult(
            response = SabrPlaybackWindowReadyResponse(
                sessionId = holder.sessionToken,
                generation = holder.activeGeneration(),
                ready = true,
                retryAfterMs = null,
                durationMs = holder.durationMs(),
                endOfStream = live?.active != true && audio.atEnd && video.atEnd,
                audio = audio.track,
                video = video.track,
                startTimeMs = playbackStartMs,
                live = live?.toResponse(),
            ),
            blockedBy = blocked?.blockedBy,
            blockedRequests = listOfNotNull(video.blockedRequest, audio.blockedRequest),
            isReady = audio.covers(holder.readyEndMs(holder.audioFormat, requestedReadyEndMs)) &&
                video.covers(holder.readyEndMs(holder.videoFormat, requestedReadyEndMs)),
        )
    }
    private suspend fun buildAudioOnly(
        holder: SabrSessionHolder,
        request: SabrPlaybackWindowRequest,
        live: SabrLivePlaybackResponse?,
    ): SabrPlaybackWindowBuildResult {
        val audio = trackBuilder.buildTrack(holder, holder.audioFormat, request, request.playerTimeMs, live?.active == true)
        val playbackStartMs = resolvedPlaybackStartMs(request.playerTimeMs, live?.active == true, audio.track)
        val readyEndMs = playbackStartMs + readyAheadMs(request, live?.active == true)
        return SabrPlaybackWindowBuildResult(
            response = SabrPlaybackWindowReadyResponse(
                sessionId = holder.sessionToken,
                generation = holder.activeGeneration(),
                ready = true,
                retryAfterMs = null,
                durationMs = holder.durationMs(),
                endOfStream = live?.active != true && audio.atEnd,
                audio = audio.track,
                startTimeMs = playbackStartMs,
                live = live,
            ),
            blockedBy = audio.blockedBy,
            blockedRequests = listOfNotNull(audio.blockedRequest),
            isReady = audio.covers(holder.readyEndMs(holder.audioFormat, readyEndMs)),
        )
    }
    private fun blockedTrack(audio: TrackBuildResult, video: TrackBuildResult): TrackBuildResult? =
        sequenceOf(video, audio)
            .filter { it.blockedRequest != null }
            .minByOrNull { it.coveredEndMs }
}
