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
                val nowMs = clock()
                val backoffRemainingMs = holder.session.demandBackoffRemainingMs
                if (holder.isLiveDemandOutsideRecoverableWindow(inFlightDemand.request) &&
                    SabrDemandAttemptFinisher.expireStalledInFlightDemand(
                        holder,
                        inFlightDemand,
                        recoverable = true,
                        nowMs = nowMs,
                        reason = "outside_recoverable_window",
                    )
                ) {
                    return true
                }
                if (inFlightDemand.futureLiveRequest) holder.setPlaybackState(SabrPlaybackState.WAITING_FOR_LIVE)
                val lastProgressAtMs = inFlightDemand.observeProgress(holder.session.mediaProgressVersion, nowMs)
                val completedIdle = holder.session.getCachedSegment(inFlightDemand.request) != null &&
                    nowMs - lastProgressAtMs >= SabrPumpPolicy.COMPLETED_DEMAND_IDLE_MS
                if (completedIdle && SabrDemandAttemptFinisher.interruptCompletedInFlightDemand(holder, inFlightDemand)) {
                    return true
                }
                val deadlineAtMs = deadline.deadlineAtMs(
                    inFlightDemand.identity,
                    inFlightDemand.registeredAtMs,
                    nowMs,
                    backoffRemainingMs,
                )
                if (nowMs >= deadlineAtMs) {
                    val recoverableLiveDemand =
                        holder.livePlaybackSnapshot()?.active == true &&
                            !holder.isLiveDemandOutsideRecoverableWindow(inFlightDemand.request)
                    if (recoverableLiveDemand) {
                        SabrDemandAttemptFinisher.interruptStalledInFlightDemand(
                            holder,
                            inFlightDemand,
                            nowMs = nowMs,
                            expectedDelayMs = (deadlineAtMs - inFlightDemand.registeredAtMs).coerceAtLeast(0L),
                            reason = if (inFlightDemand.futureLiveRequest) {
                                "future_live_not_published"
                            } else {
                                "network_slow"
                            },
                        )
                    } else if (SabrDemandAttemptFinisher.expireStalledInFlightDemand(
                            holder,
                            inFlightDemand,
                            nowMs = nowMs,
                            expectedDelayMs = (deadlineAtMs - inFlightDemand.registeredAtMs).coerceAtLeast(0L),
                            reason = "terminal_deadline",
                        )
                    ) {
                        return true
                    }
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
                    SabrDemandAttemptFinisher.expireStalledDemand(
                        holder,
                        request,
                        identity,
                        recoverable = true,
                        reason = "outside_recoverable_window",
                    )
                ) {
                    return true
                }
            }
            if (futureLiveRequest) {
                holder.setPlaybackState(SabrPlaybackState.WAITING_FOR_LIVE)
                delay(nextCheckDelayMs(holder.session.demandBackoffRemainingMs, futureLiveRequest))
                continue
            }
            val recoverableLiveRequest =
                holder.livePlaybackSnapshot()?.active == true && !outsideLiveWindow
            if (recoverableLiveRequest && holder.inFlightSegmentDemand() == null) {
                val identity = holder.segmentDemandIdentity(request)
                val requeued = synchronized(holder) {
                    holder.inFlightSegmentDemand() == null &&
                        identity != null &&
                        holder.requeueSegmentDemand(request, identity, clock())
                }
                if (requeued) {
                    holder.setPlaybackState(SabrPlaybackState.WAITING_FOR_LIVE)
                    delay(nextCheckDelayMs(holder.session.demandBackoffRemainingMs, true))
                    continue
                }
            }
            val identity = holder.segmentDemandIdentity(request)
            val registeredAtMs = identity?.let { holder.segmentDemandRegisteredAtMs(request, it) }
            val nowMs = clock()
            val backoffRemainingMs = holder.session.demandBackoffRemainingMs
            val deadlineAtMs = identity?.let { demandIdentity ->
                registeredAtMs?.let { registered ->
                    deadline.deadlineAtMs(demandIdentity, registered, nowMs, backoffRemainingMs)
                }
            }
            if (identity != null && registeredAtMs != null && deadlineAtMs != null && nowMs >= deadlineAtMs &&
                SabrDemandAttemptFinisher.expireStalledDemand(
                    holder,
                    request,
                    identity,
                    nowMs = nowMs,
                    expectedDelayMs = (deadlineAtMs - registeredAtMs).coerceAtLeast(0L),
                    reason = "terminal_deadline",
                )
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
