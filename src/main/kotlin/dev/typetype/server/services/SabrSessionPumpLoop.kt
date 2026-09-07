package dev.typetype.server.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.withLock
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.localization.Localization
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrRecoverableException
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrSession
import java.io.IOException

internal class SabrSessionPumpLoop(
    private val unauthorizedRecovery: SabrUnauthorizedResponseRecovery = SabrUnauthorizedResponseRecovery { null },
    private val runtimeFactory: () -> SabrPumpRuntime = { SabrPumpRuntime() },
    private val onResolved: (SabrSessionHolder, SabrMediaSegment) -> Unit = { _, _ -> },
) {
    suspend fun run(isAlive: () -> Boolean, holder: SabrSessionHolder, intervalMs: Long) {
        val localization = Localization("en", "US")
        val runtime = runtimeFactory()
        var consecutiveIoErrors = 0
        while (isAlive() && holder.playbackState() != SabrPlaybackState.TERMINAL) {
            try {
                val wakeVersion = holder.pumpWakeVersion()
                val requestNumber = holder.session.requestNumber
                val immediate = holder.pumpMutex.withLock { pumpRound(holder, localization, runtime) }
                if (holder.session.requestNumber != requestNumber) consecutiveIoErrors = 0
                if (immediate) delay(0L)
                else holder.awaitPumpWake(wakeVersion, pumpDemandDelayMs(holder, intervalMs))
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                consecutiveIoErrors++
                if (consecutiveIoErrors >= SabrPumpPolicy.MAX_CONSECUTIVE_IO_ERRORS) {
                    holder.recordNetworkFailure(error.message)
                    return
                }
                delay(SabrPumpPolicy.ERROR_RETRY_MS)
            } catch (error: SabrRecoverableException) {
                holder.failTerminal(sabrRecoverableFailureMessage(error.message))
                return
            } catch (error: SabrProtectedNoMediaException) {
                holder.failTerminal(error.message)
                return
            } catch (error: ExtractionException) {
                holder.failTerminal(error.message)
                return
            } catch (error: OutOfMemoryError) {
                holder.failTerminal(error.message)
                return
            } catch (error: Exception) {
                SabrPumpLogger.failure(holder, error)
                delay(SabrPumpPolicy.ERROR_RETRY_MS)
            }
        }
        if (holder.playbackState() != SabrPlaybackState.TERMINAL &&
            holder.playbackState() != SabrPlaybackState.NETWORK_FAILED
        ) {
            holder.setPlaybackState(SabrPlaybackState.STOPPED)
        }
    }

    private suspend fun pumpRound(
        holder: SabrSessionHolder,
        localization: Localization,
        runtime: SabrPumpRuntime,
    ): Boolean {
        preparePumpEviction(holder)
        if (holder.prepareStartupBootstrapPump()) {
            holder.setPlaybackState(SabrPlaybackState.REQUESTING)
            pumpOnce(holder, localization, runtime)
            holder.setPlaybackState(SabrPlaybackState.IDLE)
            return true
        }
        holder.consumeRefetch()?.let { request ->
            if (holder.session.isBeyondEnd(request) && !holder.isFutureLiveRequest(request)) {
                holder.clearSegmentDemand(request)
                return true
            }
            runtime.activateSeekMode()
            holder.setPlaybackState(SabrPlaybackState.REPOSITIONING)
            holder.prepareForExplicitRewind(request)
            SabrPumpLogger.start(holder, "refetch", request)
            val pumped = pumpOnce(holder, localization, runtime)
            SabrPumpLogger.finish(holder, "refetch", request, pumped)
            holder.setPlaybackState(SabrPlaybackState.IDLE)
            return true
        }
        holder.consumeForwardSeek()?.let { request ->
            if (holder.session.isBeyondEnd(request) && !holder.isFutureLiveRequest(request)) {
                holder.clearSegmentDemand(request)
                return true
            }
            runtime.activateSeekMode()
            holder.setPlaybackState(SabrPlaybackState.REPOSITIONING)
            holder.prepareForExplicitForwardJump(request)
            SabrPumpLogger.start(holder, "forward_seek", request)
            val pumped = pumpUntilCached(holder, localization, request, runtime)
            SabrPumpLogger.finish(holder, "forward_seek", request, pumped.segmentCount)
            holder.setPlaybackState(SabrPlaybackState.IDLE)
            return true
        }
        holder.nextSegmentDemand()?.let { request ->
            val demandIdentity = holder.segmentDemandIdentity(request) ?: return true
            val wasFutureLiveRequest = holder.isFutureLiveRequest(request)
            if (!holder.beginInFlightSegmentDemand(request, demandIdentity, wasFutureLiveRequest)) return true
            try {
                SabrPumpLogger.start(holder, "demand", request)
                runtime.beginDemand(demandIdentity)
                val result = pumpDemand(holder, localization, request, runtime)
                return SabrDemandAttemptFinisher.finish(
                    holder,
                    request,
                    demandIdentity,
                    result,
                    runtime,
                    wasFutureLiveRequest,
                    onResolved = { onResolved(holder, it) },
                )
            } finally {
                holder.finishInFlightSegmentDemand(demandIdentity)
            }
        }
        if (holder.livePlaybackSnapshot()?.active == true) {
            return pumpLiveReadAhead(
                holder = holder,
                runtime = runtime,
                pump = { pumpOnce(holder, localization, runtime) },
                onResolved = onResolved,
            )
        }
        if (holder.session.requestNumber == 0 && !holder.expectsLive()) return false
        if (holder.session.isComplete && holder.livePlaybackSnapshot()?.active != true && !holder.hasPendingSeek()) return false
        if (runtime.isThrottled(holder)) {
            holder.setPlaybackState(SabrPlaybackState.THROTTLED)
            return false
        }
        holder.setPlaybackState(SabrPlaybackState.REQUESTING)
        val edgeMs = holder.session.streamState.getMinBufferedEndMs()
        holder.session.streamState.setPlayerTimeMs(runtime.requestPlayerTimeMs(holder, edgeMs))
        pumpOnce(holder, localization, runtime)
        holder.setPlaybackState(SabrPlaybackState.IDLE)
        return false
    }
    private suspend fun pumpOnce(holder: SabrSessionHolder, localization: Localization, runtime: SabrPumpRuntime): Int {
        return try {
            runInterruptible(Dispatchers.IO) { holder.withPlayerContext { pumpOnceStreaming(localization) } }
                .also { unauthorizedRecovery.verify(holder) }
                .also { runtime.verifyProtectedResponse(holder) }
        } finally {
            runtime.recordRequest()
        }
    }

    private suspend fun pumpUntilCached(
        holder: SabrSessionHolder,
        localization: Localization,
        request: SabrSegmentRequest,
        runtime: SabrPumpRuntime,
    ): YoutubeSabrSession.DemandResponseResult {
        return try {
            runInterruptible(Dispatchers.IO) { holder.withPlayerContext { pumpOnceStreamingForDemand(localization, request) } }
                .also { unauthorizedRecovery.verify(holder) }
                .also { runtime.verifyProtectedResponse(holder) }
        } finally {
            runtime.recordRequest()
        }
    }

    private suspend fun pumpDemand(
        holder: SabrSessionHolder,
        localization: Localization,
        request: SabrSegmentRequest,
        runtime: SabrPumpRuntime,
    ): YoutubeSabrSession.DemandResponseResult {
        val edgeMs = holder.session.streamState.getMinBufferedEndMs()
        val startMs = holder.playbackSegmentStartMs(request.format, request.sequenceNumber)
        holder.setReaderPosition(request.format, startMs)
        if (holder.livePlaybackSnapshot()?.active == true) {
            if (holder.isHistoricalLiveRequest(request)) {
                holder.setPlaybackState(SabrPlaybackState.REPOSITIONING)
                holder.prepareForHistoricalLiveRewind(request)
                return withTargetedRequestShape(holder, request, prepareSession = false) {
                    pumpUntilCached(holder, localization, request, runtime)
                }
            }
            holder.setPlaybackState(SabrPlaybackState.REQUESTING)
            return withLiveContinuationRequestShape(holder) {
                pumpUntilCached(holder, localization, request, runtime)
            }
        }
        return when {
            startMs < edgeMs -> {
                holder.setPlaybackState(SabrPlaybackState.REPOSITIONING)
                holder.session.prepareForRewind(request)
                pumpUntilCached(holder, localization, request, runtime)
            }
            startMs > edgeMs + DEMAND_FORWARD_JUMP_MS -> {
                holder.setPlaybackState(SabrPlaybackState.REPOSITIONING)
                holder.session.prepareForForwardJump(request)
                pumpUntilCached(holder, localization, request, runtime)
            }
            else -> {
                holder.setPlaybackState(SabrPlaybackState.REQUESTING)
                holder.session.streamState.setPlayerTimeMs(runtime.demandPlayerTimeMs(holder, edgeMs))
                pumpUntilCached(holder, localization, request, runtime)
            }
        }
    }

}
