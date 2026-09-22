package dev.typetype.server.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrSession

internal suspend fun runSabrDemandAttempt(
    holder: SabrSessionHolder,
    request: SabrSegmentRequest,
    identity: String,
    wasFutureLiveRequest: Boolean,
    runtime: SabrPumpRuntime,
    pump: suspend () -> YoutubeSabrSession.DemandResponseResult,
    onResolved: (SabrMediaSegment) -> Unit,
): Boolean {
    if (!holder.beginInFlightSegmentDemand(request, identity, wasFutureLiveRequest)) return true
    try {
        SabrPumpLogger.start(holder, "demand", request)
        runtime.beginDemand(identity)
        return coroutineScope {
            val attempt = async(start = CoroutineStart.UNDISPATCHED) {
                holder.recordInFlightAttemptStarted()
                try {
                    pump()
                } finally {
                    holder.recordInFlightAttemptFinished()
                }
            }
            holder.registerInFlightDemandCancellation(identity) {
                attempt.cancel(CancellationException("SABR demand interrupted"))
            }
            try {
                val result = attempt.await()
                val interruption = holder.consumeInFlightDemandInterruption(identity)
                if (interruption != null) {
                    return@coroutineScope finishInterruptedDemand(holder, request, identity, runtime, interruption)
                }
                SabrDemandAttemptFinisher.finish(
                    holder,
                    request,
                    identity,
                    result,
                    runtime,
                    wasFutureLiveRequest,
                    onResolved = { onResolved(it) },
                )
            } catch (error: CancellationException) {
                val interruption = holder.consumeInFlightDemandInterruption(identity)
                    ?: throw error
                finishInterruptedDemand(holder, request, identity, runtime, interruption)
            } finally {
                holder.clearInFlightDemandCancellation(identity)
            }
        }
    } finally {
        holder.finishInFlightSegmentDemand(identity)
    }
}

private fun finishInterruptedDemand(
    holder: SabrSessionHolder,
    request: SabrSegmentRequest,
    identity: String,
    runtime: SabrPumpRuntime,
    interruption: SabrDemandInterruption,
): Boolean = synchronized(holder) {
    runtime.finishDemand(identity)
    when (interruption.action) {
        SabrDemandInterruptionAction.WAIT_FOR_LIVE -> {
            if (holder.isSegmentDemandActive(request, identity)) {
                holder.requeueSegmentDemand(request, identity, interruption.atMs)
            }
            holder.setPlaybackState(SabrPlaybackState.WAITING_FOR_LIVE)
            false
        }

        SabrDemandInterruptionAction.RESTART -> {
            if (holder.playbackState() != SabrPlaybackState.TERMINAL &&
                holder.playbackState() != SabrPlaybackState.NETWORK_FAILED
            ) {
                holder.setPlaybackState(SabrPlaybackState.IDLE)
            }
            true
        }
    }
}
