package dev.typetype.server.services

import kotlinx.coroutines.delay

class SabrDemandWatchdog(
    private val clock: () -> Long = System::currentTimeMillis,
    private val intervalMs: Long = SabrPumpPolicy.IDLE_POLL_MS,
) {
    suspend fun monitor(isAlive: () -> Boolean, holder: SabrSessionHolder): Boolean {
        val deadline = SabrDemandDeadline(SabrPumpPolicy.DEMAND_TARGET_DEADLINE_MS)
        while (isAlive()) {
            val state = holder.playbackState()
            if (state == SabrPlaybackState.TERMINAL || state == SabrPlaybackState.NETWORK_FAILED) return false
            val inFlightDemand = holder.inFlightSegmentDemand()
            if (inFlightDemand != null) {
                if (holder.isLiveDemandOutsideRecoverableWindow(inFlightDemand.request) &&
                    SabrDemandAttemptFinisher.expireStalledInFlightDemand(holder, inFlightDemand, recoverable = true)
                ) {
                    return true
                }
                if (inFlightDemand.futureLiveRequest) holder.setPlaybackState(SabrPlaybackState.WAITING_FOR_LIVE)
                val nowMs = clock()
                val backoffRemainingMs = holder.session.demandBackoffRemainingMs
                val lastProgressAtMs = inFlightDemand.observeProgress(holder.session.mediaProgressVersion, nowMs)
                val completedIdle = holder.session.getCachedSegment(inFlightDemand.request) != null &&
                    nowMs - lastProgressAtMs >= SabrPumpPolicy.COMPLETED_DEMAND_IDLE_MS
                if (completedIdle && SabrDemandAttemptFinisher.interruptCompletedInFlightDemand(holder, inFlightDemand)) {
                    return true
                }
                if (deadline.isExpired(
                        inFlightDemand.identity,
                        inFlightDemand.registeredAtMs,
                        nowMs,
                        backoffRemainingMs,
                    ) &&
                    SabrDemandAttemptFinisher.expireStalledInFlightDemand(holder, inFlightDemand)
                ) {
                    return true
                }
                delay(nextCheckDelayMs(backoffRemainingMs, inFlightDemand.futureLiveRequest))
                continue
            }
            val request = holder.nextSegmentDemand()
            if (request == null) {
                delay(intervalMs)
                continue
            }
            val outsideLiveWindow = holder.isLiveDemandOutsideRecoverableWindow(request)
            val futureLiveRequest = holder.isFutureLiveRequest(request)
            if (outsideLiveWindow) {
                val identity = holder.segmentDemandIdentity(request)
                if (identity != null &&
                    SabrDemandAttemptFinisher.expireStalledDemand(holder, request, identity, recoverable = true)
                ) {
                    return true
                }
            }
            if (futureLiveRequest) {
                holder.setPlaybackState(SabrPlaybackState.WAITING_FOR_LIVE)
            }
            val identity = holder.segmentDemandIdentity(request)
            val registeredAtMs = identity?.let { holder.segmentDemandRegisteredAtMs(request, it) }
            val nowMs = clock()
            val backoffRemainingMs = holder.session.demandBackoffRemainingMs
            if (identity != null && registeredAtMs != null && deadline.isExpired(
                    identity,
                    registeredAtMs,
                    nowMs,
                    backoffRemainingMs,
                ) &&
                SabrDemandAttemptFinisher.expireStalledDemand(holder, request, identity, futureLiveRequest)
            ) {
                return true
            }
            delay(nextCheckDelayMs(backoffRemainingMs, futureLiveRequest))
        }
        return false
    }

    private fun nextCheckDelayMs(backoffRemainingMs: Long, futureLiveRequest: Boolean): Long =
        maxOf(intervalMs, LIVE_EDGE_POLL_MS.takeIf { futureLiveRequest } ?: 0L)
}
