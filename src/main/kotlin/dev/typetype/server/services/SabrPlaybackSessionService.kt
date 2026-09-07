package dev.typetype.server.services

import kotlinx.coroutines.withTimeoutOrNull
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat

internal class SabrPlaybackSessionService(private val sessionStore: SabrSessionStore) {
    private val mediaFetcher = SabrPlaybackMediaFetcher(sessionStore)
    suspend fun prepare(
        videoId: String,
        userId: String,
        prepared: SabrPreparedInfo,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        startTimeMs: Long,
        audioOnly: Boolean = false,
        isLive: Boolean = false,
        initialGeneration: Long = 0L,
    ): SabrPlaybackPreparation {
        val holder = sessionStore.getOrCreate(
            videoId = videoId,
            userId = userId,
            info = prepared.info,
            audioFormat = audio,
            videoFormat = video,
            initialToken = prepared.initialToken,
            startTimeMs = startTimeMs,
            startPump = false,
            purpose = SabrSessionPurpose.PLAYBACK,
            audioOnly = audioOnly,
            initialGeneration = initialGeneration,
            source = prepared.source,
        )
        if (isLive || prepared.isLive) holder.markExpectedLive()
        if (holder.expectsLive()) {
            prepareLive(holder, startTimeMs, audioOnly)
        } else {
            val initialization = SabrPlaybackInitializationPreloader.preload(
                sessionStore,
                holder,
                audioOnly,
                INITIALIZATION_PRELOAD_TIMEOUT_MS,
            )
            if (!initialization.isComplete(audioOnly)) {
                if (holder.livePlaybackSnapshot()?.active == true) {
                    holder.markExpectedLive()
                    prepareLive(holder, startTimeMs, audioOnly)
                } else {
                    val missing = initialization.missingTracks(audioOnly, video.itag, audio.itag)
                    holder.setActiveTracks(videoActive = !audioOnly, audioActive = true)
                    holder.setPlayerTimeMs(startTimeMs)
                    holder.failTerminal(sabrRecoverableFailureMessage("SABR initialization unavailable for $missing"))
                    return SabrPlaybackPreparation(holder, startTimeMs, ready = false)
                }
            }
        }
        return SabrPlaybackStarter.start(
            sessionStore,
            holder,
            holder.resolvePlaybackStartMs(startTimeMs),
            audioOnly,
            startPump = true,
        )
    }

    private suspend fun prepareLive(holder: SabrSessionHolder, startTimeMs: Long, audioOnly: Boolean) {
        holder.setActiveTracks(videoActive = !audioOnly, audioActive = true)
        holder.session.streamState.setSelectVideoFormatBeforeAudio(!audioOnly)
        if (startTimeMs == 0L) {
            holder.session.streamState.setPlayerTimeMs(OFFICIAL_LIVE_EDGE_PLAYER_TIME_MS)
            holder.session.streamState.setWriteTopLevelPlayerTimeMs(false)
        }
        sessionStore.ensureWarmed(holder, LIVE_INITIAL_PUMPS)
    }

    suspend fun seek(
        source: SabrSessionHolder,
        prepared: SabrPreparedInfo,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        playerTimeMs: Long,
        audioOnly: Boolean = false,
    ): SabrPlaybackPreparation {
        if (source.matches(audio, video)) return seekExisting(source, playerTimeMs, audioOnly)
        return prepare(
            videoId = source.key.videoId,
            userId = source.key.userId,
            prepared = prepared,
            audio = audio,
            video = video,
            startTimeMs = playerTimeMs,
            audioOnly = audioOnly,
            isLive = source.expectsLive() || prepared.isLive,
            initialGeneration = source.nextReplacementGeneration(),
        )
    }

    fun seekExisting(
        holder: SabrSessionHolder,
        playerTimeMs: Long,
        audioOnly: Boolean = false,
    ): SabrPlaybackPreparation = synchronized(holder) {
        holder.clearSegmentDemands()
        val generation = holder.advancePlaybackGeneration(playerTimeMs)
        holder.setActiveTracks(videoActive = !audioOnly, audioActive = true)
        holder.setRequestedSeekTimeMs(playerTimeMs)
        holder.session.streamState.setSelectVideoFormatBeforeAudio(playerTimeMs > SABR_SEEK_FORMAT_ORDER_MS)
        holder.requestPlaybackReposition(playerTimeMs, generation)
        sessionStore.startPump(holder)
        SabrPlaybackPreparation(holder, playerTimeMs, ready = false)
    }

    fun lookup(sessionId: String): SabrSessionHolder? = sessionStore.lookupByToken(sessionId)
        ?.takeIf { it.key.purpose == SabrSessionPurpose.PLAYBACK }

    fun startPump(holder: SabrSessionHolder): Unit = sessionStore.startPump(holder)

    fun warmPlayback(holder: SabrSessionHolder): Unit = sessionStore.warmPlaybackAsync(holder)

    suspend fun fetchInitialization(
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
        timeoutMs: Long,
        generation: Long,
    ): SabrPlaybackSegmentResult {
        val activeGeneration = holder.activeGeneration()
        if (generation > activeGeneration) return SabrPlaybackSegmentResult.InvalidGeneration
        val request = SabrSegmentRequest.initialization(format)
        if (generation < activeGeneration) return staleMedia(holder, request)
        val bytes = withTimeoutOrNull(timeoutMs) { sessionStore.fetchInitializationData(holder, format) }
        return if (bytes == null) SabrPlaybackSegmentResult.Retry(holder, PREPARING) else {
            SabrPlaybackSegmentResult.Ready(format.mimeType.orEmpty(), bytes)
        }
    }

    suspend fun fetchMedia(
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
        sequence: Int,
        timeoutMs: Long,
        generation: Long,
    ): SabrPlaybackSegmentResult {
        if (sequence < 1) return SabrPlaybackSegmentResult.InvalidSequence
        val activeGeneration = holder.activeGeneration()
        if (generation > activeGeneration) return SabrPlaybackSegmentResult.InvalidGeneration
        val request = SabrSegmentRequest.media(format, sequence)
        if (generation < activeGeneration) return staleMedia(holder, request)
        return mediaFetcher.fetch(holder, request, timeoutMs, generation)
    }

    private suspend fun staleMedia(holder: SabrSessionHolder, request: SabrSegmentRequest): SabrPlaybackSegmentResult =
        sessionStore.cachedSegment(holder, request)
            ?.let { SabrPlaybackSegmentResult.Ready(it.mimeType, it.bytes) }
            ?: SabrPlaybackSegmentResult.Stale(holder)

    private fun SabrSessionHolder.matches(audio: YoutubeSabrFormat, video: YoutubeSabrFormat): Boolean =
        audioFormat.itag == audio.itag && audioFormat.audioTrackId == audio.audioTrackId && videoFormat.itag == video.itag

    private companion object {
        const val PREPARING = "preparing"
        const val INITIALIZATION_PRELOAD_TIMEOUT_MS = 6_000L
        const val LIVE_INITIAL_PUMPS = 8
        const val OFFICIAL_LIVE_EDGE_PLAYER_TIME_MS = 9_007_199_254_740_991L
    }
}
