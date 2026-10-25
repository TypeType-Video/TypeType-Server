package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.SabrPlaybackDiagnostics
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.pendingSegmentDemandSummary
import dev.typetype.server.services.livePlaybackSnapshot
import dev.typetype.server.services.liveRetryAfterMs
import dev.typetype.server.services.resolvePlaybackStartMs
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import dev.typetype.server.sabr.SabrSegmentRequest

internal class SabrPlaybackWindowHandler(
    private val sabrSessionStore: SabrSessionStore,
    private val awaiter: SabrPlaybackWindowAwaiter = SabrPlaybackWindowAwaiter(),
) {
    private val windowBuilder = SabrPlaybackWindowBuilder(sabrSessionStore)
    private val recovery = SabrPlaybackRecovery(sabrSessionStore)

    suspend fun post(call: ApplicationCall, sessionId: String) {
        val request = runCatching { call.receive<SabrPlaybackWindowRequest>() }.getOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid window request"))
        val holder = validatedHolder(call, sessionId, request) ?: return

        holder.setPlaybackRate(request.playbackRate)
        holder.setActiveTracks(videoActive = !request.audioOnly, audioActive = true)
        holder.setPlayerTimeMs(holder.resolvePlaybackStartMs(request.playerTimeMs))
        holder.applyClientPreferences()
        sabrSessionStore.startPump(holder)

        val window = awaitWithTargetedPrefetch(holder, request)
        if (holder.canServe(window)) {
            holder.initialWindowReady = true
            return call.respond(HttpStatusCode.OK, window.response)
        }
        call.respond(HttpStatusCode.Accepted, holder.preparingResponse(request, window))
    }

    suspend fun position(call: ApplicationCall, sessionId: String) {
        val request = runCatching { call.receive<SabrPlaybackPositionRequest>() }.getOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid position request"))
        val holder = validatedHolder(call, sessionId, request) ?: return
        val response = synchronized(holder) {
            if (request.generation == holder.activeGeneration()) {
                holder.setPlaybackRate(request.playbackRate)
                holder.setActiveTracks(videoActive = !request.audioOnly, audioActive = true)
                holder.setPlayerTimeMs(holder.resolvePlaybackStartMs(request.playerTimeMs))
                holder.applyClientPreferences()
            }
            SabrPlaybackPositionResponse(
                sessionId = holder.sessionToken,
                generation = holder.activeGeneration(),
                playerTimeMs = holder.playerTimeMs(),
                readerHeadMs = holder.readerHeadMs(),
                readerTailMs = holder.readerTailMs(),
                bufferedEdgeMs = holder.session.streamState.getMinBufferedEndMs(),
                live = holder.livePlaybackSnapshot()?.toResponse(),
            )
        }
        call.respond(response)
    }

    suspend fun prefetch(call: ApplicationCall, sessionId: String) {
        val request = runCatching { call.receive<SabrPlaybackWindowRequest>() }.getOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid prefetch request"))
        val holder = validatedHolder(call, sessionId, request) ?: return
        holder.setPlaybackRate(request.playbackRate)
        holder.setActiveTracks(videoActive = !request.audioOnly, audioActive = true)
        holder.setPlayerTimeMs(holder.resolvePlaybackStartMs(request.playerTimeMs))
        holder.applyClientPreferences()
        sabrSessionStore.startPump(holder)
        val window = awaitWithTargetedPrefetch(holder, request)
        val ready = holder.canServe(window)
        if (ready) holder.initialWindowReady = true
        call.respond(if (ready) HttpStatusCode.OK else HttpStatusCode.Accepted, holder.prefetchResponse(request, window, ready))
    }

    suspend fun segments(call: ApplicationCall, sessionId: String) {
        val request = runCatching { call.receive<SabrPlaybackWindowRequest>() }.getOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid segments request"))
        val holder = validatedHolder(call, sessionId, request) ?: return
        holder.setPlaybackRate(request.playbackRate)
        holder.setActiveTracks(videoActive = !request.audioOnly, audioActive = true)
        holder.applyClientPreferences()
        val window = awaiter.await(holder, windowBuilder.build(holder, request)) { windowBuilder.build(holder, request) }
        if (holder.canServe(window)) {
            holder.initialWindowReady = true
            return call.respond(HttpStatusCode.OK, window.response)
        }
        call.respond(HttpStatusCode.Accepted, holder.preparingResponse(request, window))
    }

    private suspend fun awaitWithTargetedPrefetch(
        holder: SabrSessionHolder,
        request: SabrPlaybackWindowRequest,
    ): SabrPlaybackWindowBuildResult {
        val requested = mutableSetOf<String>()
        return awaiter.await(holder, buildWithTargetedPrefetch(holder, request, requested)) {
            buildWithTargetedPrefetch(holder, request, requested)
        }
    }

    private suspend fun buildWithTargetedPrefetch(
        holder: SabrSessionHolder,
        request: SabrPlaybackWindowRequest,
        requested: MutableSet<String>,
    ): SabrPlaybackWindowBuildResult {
        val window = windowBuilder.build(holder, request)
        window.blockedRequests.filter { requested.add(it.summary()) }.forEach { blockedRequest ->
            sabrSessionStore.requestSegmentDemand(holder, blockedRequest, request.generation)
        }
        return window
    }

    private fun SabrSessionHolder.canServe(window: SabrPlaybackWindowBuildResult): Boolean =
        window.isReady && terminalFailure() == null && networkFailure() == null

    private suspend fun SabrSessionHolder.preparingResponse(
        request: SabrPlaybackWindowRequest,
        window: SabrPlaybackWindowBuildResult,
    ): SabrPlaybackWindowPreparingResponse =
        SabrPlaybackWindowPreparingResponse(
            sessionId = sessionToken,
            generation = activeGeneration(),
            ready = false,
            retryAfterMs = retryAfterMs(window),
            status = playbackState().name.lowercase(),
            blockedBy = SabrPlaybackDiagnostics.blocker(this) ?: window.blockedBy ?: "window pending",
            playerTimeMs = request.playerTimeMs.coerceAtLeast(0L),
            readerHeadMs = readerHeadMs(),
            readerTailMs = readerTailMs(),
            bufferedEdgeMs = session.streamState.getMinBufferedEndMs(),
            pendingRefetch = pendingRefetchRequest()?.summary(),
            pendingForwardSeek = pendingForwardSeekRequest()?.summary(),
            pendingSegmentDemand = pendingSegmentDemandSummary(),
            terminalError = terminalFailure() ?: networkFailure(),
            recoveryAction = recovery.action(this),
            retryVideoItags = recovery.retryVideoItags(),
            live = livePlaybackSnapshot()?.toResponse(),
        )

    private suspend fun SabrSessionHolder.prefetchResponse(
        request: SabrPlaybackWindowRequest,
        window: SabrPlaybackWindowBuildResult,
        ready: Boolean,
    ): SabrPlaybackPrefetchResponse = SabrPlaybackPrefetchResponse(
        sessionId = sessionToken,
        generation = activeGeneration(),
        ready = ready,
        retryAfterMs = if (ready) null else retryAfterMs(window),
        status = playbackState().name.lowercase(),
        segmentsUrl = "${SabrPlaybackPaths.mediaBasePath(sessionToken)}/segments",
        stateUrl = "${SabrPlaybackPaths.mediaBasePath(sessionToken)}/state",
        blockedBy = if (ready) null else SabrPlaybackDiagnostics.blocker(this) ?: window.blockedBy ?: "window pending",
        playerTimeMs = request.playerTimeMs.coerceAtLeast(0L),
        readerHeadMs = readerHeadMs(),
        readerTailMs = readerTailMs(),
        bufferedEdgeMs = session.streamState.getMinBufferedEndMs(),
        pendingRefetch = pendingRefetchRequest()?.summary(),
        pendingForwardSeek = pendingForwardSeekRequest()?.summary(),
        pendingSegmentDemand = pendingSegmentDemandSummary(),
        terminalError = terminalFailure() ?: networkFailure(),
        recoveryAction = recovery.action(this),
        retryVideoItags = recovery.retryVideoItags(),
        live = livePlaybackSnapshot()?.toResponse(),
    )

    private suspend fun validatedHolder(
        call: ApplicationCall,
        sessionId: String,
        request: SabrPlaybackWindowRequest,
    ): SabrSessionHolder? {
        val holder = sabrSessionStore.lookupByToken(sessionId)
            ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("No active SABR playback session")).let { null }
        if (!holder.matches(request)) {
            return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Window formats do not match session")).let { null }
        }
        if (request.generation != holder.activeGeneration()) {
            return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid generation")).let { null }
        }
        if (!request.playbackRate.isSupportedSabrPlaybackRate()) {
            return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid playback rate")).let { null }
        }
        return holder
    }

    private suspend fun validatedHolder(
        call: ApplicationCall,
        sessionId: String,
        request: SabrPlaybackPositionRequest,
    ): SabrSessionHolder? {
        val holder = sabrSessionStore.lookupByToken(sessionId)
            ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("No active SABR playback session")).let { null }
        if (!holder.matches(request)) {
            return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Position formats do not match session")).let { null }
        }
        if (!request.playbackRate.isSupportedSabrPlaybackRate()) {
            return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid playback rate")).let { null }
        }
        return holder
    }

    private fun SabrSessionHolder.matches(request: SabrPlaybackWindowRequest): Boolean =
        request.videoItag == videoFormat.itag && request.audioItag == audioFormat.itag && request.audioTrackId == audioFormat.audioTrackId

    private fun SabrSessionHolder.matches(request: SabrPlaybackPositionRequest): Boolean =
        request.videoItag == videoFormat.itag && request.audioItag == audioFormat.itag && request.audioTrackId == audioFormat.audioTrackId

    private fun SabrSegmentRequest.summary(): String = "${format.itag}:$sequenceNumber"

    private fun SabrSessionHolder.retryAfterMs(window: SabrPlaybackWindowBuildResult): Long =
        playbackWindowRetryAfterMs(
            isLive = expectsLive() || livePlaybackSnapshot()?.active == true,
            initialWindowReady = initialWindowReady,
            defaultRetryAfterMs = liveRetryAfterMs(window.blockedRequests),
        )
}
