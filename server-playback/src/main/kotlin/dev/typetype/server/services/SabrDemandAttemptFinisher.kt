package dev.typetype.server.services

import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrSession

object SabrDemandAttemptFinisher {
    fun interruptCompletedInFlightDemand(holder: SabrSessionHolder, demand: SabrInFlightDemand): Boolean =
        synchronized(holder) {
            val state = holder.playbackState()
            if (state == SabrPlaybackState.TERMINAL || state == SabrPlaybackState.NETWORK_FAILED) return@synchronized false
            if (holder.inFlightSegmentDemand()?.identity != demand.identity) return@synchronized false
            if (holder.session.getCachedSegment(demand.request) == null) return@synchronized false
            holder.requestInFlightDemandInterruption(
                demand.identity,
                SabrDemandInterruption(
                    action = SabrDemandInterruptionAction.RESTART,
                    atMs = System.currentTimeMillis(),
                    reason = "completed_demand",
                ),
            )
            holder.setPlaybackState(SabrPlaybackState.IDLE)
            true
        }

    fun interruptStalledInFlightDemand(
        holder: SabrSessionHolder,
        demand: SabrInFlightDemand,
        nowMs: Long,
        expectedDelayMs: Long,
        reason: String,
    ): Boolean = synchronized(holder) {
        val state = holder.playbackState()
        if (state == SabrPlaybackState.TERMINAL || state == SabrPlaybackState.NETWORK_FAILED) return@synchronized false
        if (holder.inFlightSegmentDemand()?.identity != demand.identity) return@synchronized false
        if (holder.session.getCachedSegment(demand.request) != null) return@synchronized false
        if (!holder.requeueSegmentDemand(demand.request, demand.identity, nowMs)) return@synchronized false
        holder.setPlaybackState(SabrPlaybackState.WAITING_FOR_LIVE)
        holder.requestInFlightDemandInterruption(
            demand.identity,
            SabrDemandInterruption(
                action = SabrDemandInterruptionAction.WAIT_FOR_LIVE,
                atMs = nowMs,
                reason = "future_live_not_published",
            ),
        )
        SabrPumpLogger.expired(
            holder,
            demand.request,
            recoverable = true,
                registeredAtMs = demand.registeredAtMs,
                nowMs = nowMs,
                expectedDelayMs = expectedDelayMs,
                reason = reason,
            attempts = demand.attempts(),
            lastAttemptDurationMs = demand.lastAttemptDurationMs(),
            event = "demand_requeued",
        )
        true
    }

    fun expireStalledInFlightDemand(
        holder: SabrSessionHolder,
        demand: SabrInFlightDemand,
        recoverable: Boolean = demand.futureLiveRequest,
        nowMs: Long = System.currentTimeMillis(),
        expectedDelayMs: Long = SabrPumpPolicy.DEMAND_TARGET_DEADLINE_MS,
        reason: String = "terminal_deadline",
    ): Boolean =
        synchronized(holder) {
            val state = holder.playbackState()
            if (state == SabrPlaybackState.TERMINAL || state == SabrPlaybackState.NETWORK_FAILED) return@synchronized false
            if (holder.inFlightSegmentDemand()?.identity != demand.identity) return@synchronized false
            holder.clearSegmentDemands()
            val message = "SABR demand stalled for ${demand.request.summary()}"
            holder.requestInFlightDemandInterruption(
                demand.identity,
                SabrDemandInterruption(
                    action = SabrDemandInterruptionAction.RESTART,
                    atMs = nowMs,
                    reason = "terminal_deadline",
                ),
            )
            SabrPumpLogger.expired(
                holder,
                demand.request,
                recoverable,
            registeredAtMs = demand.registeredAtMs,
            nowMs = nowMs,
            expectedDelayMs = expectedDelayMs,
            reason = reason,
                attempts = demand.attempts(),
                lastAttemptDurationMs = demand.lastAttemptDurationMs(),
            )
            holder.failTerminal(if (recoverable) sabrRecoverableFailureMessage(message) else message)
            true
        }

    fun expireStalledDemand(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        identity: String,
        recoverable: Boolean = false,
        nowMs: Long = System.currentTimeMillis(),
        expectedDelayMs: Long = SabrPumpPolicy.DEMAND_TARGET_DEADLINE_MS,
        reason: String = "terminal_deadline",
    ): Boolean = synchronized(holder) {
        val state = holder.playbackState()
        if (state == SabrPlaybackState.TERMINAL || state == SabrPlaybackState.NETWORK_FAILED) return@synchronized false
        val current = holder.nextSegmentDemand() ?: return@synchronized false
        if (!current.matches(request) || holder.segmentDemandIdentity(current) != identity) return@synchronized false
        SabrPumpLogger.expired(
            holder,
            request,
            recoverable,
            registeredAtMs = holder.segmentDemandRegisteredAtMs(request, identity),
            nowMs = nowMs,
            expectedDelayMs = expectedDelayMs,
            reason = reason,
        )
        fail(holder, request, identity, recoverable)
    }

    fun finish(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        identity: String,
        result: YoutubeSabrSession.DemandResponseResult,
        runtime: SabrPumpRuntime,
        wasFutureLiveRequest: Boolean,
        onResolved: (SabrMediaSegment) -> Unit = {},
    ): Boolean = synchronized(holder) {
        if (!holder.isSegmentDemandActive(request, identity)) {
            runtime.finishDemand(identity)
            holder.setPlaybackState(SabrPlaybackState.IDLE)
            return@synchronized true
        }
        val resolved = holder.resolveSegmentDemand(request, identity, onResolved)
        SabrPumpLogger.finish(holder, "demand", request, result.segmentCount)
        if (!resolved && (wasFutureLiveRequest || holder.isFutureLiveRequest(request))) {
            runtime.finishDemand(identity)
            holder.setPlaybackState(SabrPlaybackState.WAITING_FOR_LIVE)
            return@synchronized false
        }
        val action = runtime.demandRecoveryAction(
            requestKey = identity,
            requestPerformed = result.requestPerformed,
            resolved = resolved,
        )
        val recovering = recover(holder, request, identity, action, runtime)
        if (holder.playbackState() != SabrPlaybackState.TERMINAL &&
            holder.playbackState() != SabrPlaybackState.WAITING_FOR_LIVE
        ) {
            holder.setPlaybackState(SabrPlaybackState.IDLE)
        }
        resolved || recovering
    }

    private fun recover(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        identity: String,
        action: SabrDemandRecoveryAction,
        runtime: SabrPumpRuntime,
    ): Boolean = when (action) {
        SabrDemandRecoveryAction.WAIT -> false
        SabrDemandRecoveryAction.READVERTISE_TRACK -> {
            runtime.activateSeekMode()
            holder.setPlaybackState(SabrPlaybackState.REPOSITIONING)
            holder.session.prepareForMissingSegment(request)
            SabrPumpLogger.recovery(holder, action, request)
            true
        }
    }

    private fun fail(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        identity: String,
        recoverable: Boolean = false,
    ): Boolean {
        val failed = holder.clearSegmentDemand(request, identity)
        if (failed) {
            holder.clearSegmentDemands()
            val message = "SABR demand stalled for ${request.summary()}"
            holder.failTerminal(if (recoverable) sabrRecoverableFailureMessage(message) else message)
        }
        return failed
    }

    private fun SabrSegmentRequest.matches(other: SabrSegmentRequest): Boolean =
        format.itag == other.format.itag && sequenceNumber == other.sequenceNumber && isInitializationSegment == other.isInitializationSegment

    private fun SabrSegmentRequest.summary(): String = "${format.itag}:$sequenceNumber"
}
