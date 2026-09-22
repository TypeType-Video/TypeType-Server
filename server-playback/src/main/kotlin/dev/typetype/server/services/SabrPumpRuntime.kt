package dev.typetype.server.services

import kotlin.math.roundToLong

class SabrPumpRuntime(private val clock: () -> Long = System::currentTimeMillis) {
    private val startedAtMs = clock()
    private val protectedResponseGuard = SabrProtectedResponseGuard()
    private var lastRequestMs = 0L
    private var seekModeUntilMs = 0L
    private var demandKey: String? = null
    private var demandTrackReadvertised = false

    fun activateSeekMode(): Unit {
        seekModeUntilMs = clock() + SabrPumpPolicy.SEEK_MODE_MS
    }

    fun recordRequest(): Unit {
        lastRequestMs = clock()
    }

    fun verifyProtectedResponse(holder: SabrSessionHolder): Unit =
        protectedResponseGuard.verify(
            holder.session.diagnosticTrace,
            holder.activeGeneration(),
            holder.playerTimeMs(),
        )

    fun requestPlayerTimeMs(holder: SabrSessionHolder, edgeMs: Long): Long =
        if (isStartupBurst()) cappedServerAheadPlayerTimeMs(holder, edgeMs) else holder.playerTimeMs()

    fun demandPlayerTimeMs(holder: SabrSessionHolder, edgeMs: Long): Long =
        cappedServerAheadPlayerTimeMs(holder, edgeMs)

    fun beginDemand(requestKey: String): Unit = ensureDemand(requestKey)

    fun finishDemand(requestKey: String): Unit {
        if (demandKey == requestKey) resetDemandRecovery()
    }

    fun demandRecoveryAction(
        requestKey: String,
        requestPerformed: Boolean,
        resolved: Boolean,
    ): SabrDemandRecoveryAction {
        if (resolved || !requestPerformed) {
            resetDemandRecovery()
            return SabrDemandRecoveryAction.WAIT
        }
        ensureDemand(requestKey)
        if (!demandTrackReadvertised) {
            demandTrackReadvertised = true
            return SabrDemandRecoveryAction.READVERTISE_TRACK
        }
        return SabrDemandRecoveryAction.WAIT
    }

    fun isThrottled(holder: SabrSessionHolder): Boolean {
        val edgeMs = holder.session.streamState.getMinBufferedEndMs()
        val aheadMs = (edgeMs - holder.playerTimeMs()).coerceAtLeast(0L)
        return aheadMs >= targetReadaheadCushionMs(holder) && !isHeartbeatDue(holder) ||
            holder.session.cachedBytes > SabrPumpPolicy.MAX_AHEAD_BYTES
    }

    fun targetReadaheadCushionMs(holder: SabrSessionHolder): Long {
        var usesLiveReadAhead = false
        val baseCushionMs = when {
            isSeekMode() -> SabrPumpPolicy.SEEK_READAHEAD_CUSHION_MS
            isStartupBurst() -> SabrPumpPolicy.STARTUP_BURST_READAHEAD_CUSHION_MS
            holder.playerTimeMs() == 0L && holder.readerTailMs() == 0L ->
                SabrPumpPolicy.STARTUP_READAHEAD_CUSHION_MS
            holder.livePlaybackSnapshot()?.active == true -> {
                usesLiveReadAhead = true
                liveReadaheadCushionMs(holder)
            }
            else -> serverReadaheadCushionMs(holder)
        }
        val maximumCushionMs = if (usesLiveReadAhead) baseCushionMs else MAX_RATE_AWARE_CUSHION_MS
        return rateAwareCushionMs(baseCushionMs, holder.playbackRate(), maximumCushionMs)
    }

    private fun liveReadaheadCushionMs(holder: SabrSessionHolder): Long {
        val bitsPerSecond = holder.videoFormat.bitrate.toLong().coerceAtLeast(0L) +
            holder.audioFormat.bitrate.toLong().coerceAtLeast(0L)
        if (bitsPerSecond <= 0L) return SabrPumpPolicy.LIVE_READAHEAD_CUSHION_MS
        val bytesPerMs = (bitsPerSecond / 8_000L).coerceAtLeast(1L)
        val memoryBoundMs = SabrPumpPolicy.MAX_AHEAD_BYTES / bytesPerMs
        return SabrPumpPolicy.LIVE_READAHEAD_CUSHION_MS.coerceAtMost(
            memoryBoundMs.coerceAtLeast(SabrPumpPolicy.MIN_SERVER_READAHEAD_CUSHION_MS),
        )
    }

    private fun serverReadaheadCushionMs(holder: SabrSessionHolder): Long {
        val policy = holder.session.streamState.nextRequestPolicy ?: return SabrPumpPolicy.READAHEAD_CUSHION_MS
        val serverTargetMs = maxOf(policy.targetAudioReadaheadMs, policy.targetVideoReadaheadMs)
        if (serverTargetMs <= 0) return SabrPumpPolicy.READAHEAD_CUSHION_MS
        return serverTargetMs.toLong().coerceIn(
            SabrPumpPolicy.MIN_SERVER_READAHEAD_CUSHION_MS,
            SabrPumpPolicy.READAHEAD_CUSHION_MS,
        )
    }

    private fun rateAwareCushionMs(baseMs: Long, playbackRate: Float, maximumMs: Long): Long =
        (baseMs * playbackRate.coerceIn(0.25f, 4.0f)).roundToLong().coerceAtMost(maximumMs)

    private fun cappedServerAheadPlayerTimeMs(holder: SabrSessionHolder, edgeMs: Long): Long =
        maxOf(holder.playerTimeMs(), edgeMs - SabrPumpPolicy.SERVER_AHEAD_MARGIN_MS)

    private fun isHeartbeatDue(holder: SabrSessionHolder): Boolean {
        val maximumMs = holder.session.streamState.nextRequestPolicy?.maxTimeSinceLastRequestMs ?: -1
        return maximumMs > 0 && lastRequestMs > 0L && clock() - lastRequestMs >= maximumMs
    }

    private fun isStartupBurst(): Boolean = clock() - startedAtMs < SabrPumpPolicy.STARTUP_BURST_MS

    private fun isSeekMode(): Boolean = clock() < seekModeUntilMs

    private fun ensureDemand(requestKey: String): Unit {
        if (demandKey == requestKey) return
        demandKey = requestKey
        demandTrackReadvertised = false
    }

    private fun resetDemandRecovery(): Unit {
        demandKey = null
        demandTrackReadvertised = false
    }

    private companion object {
        const val MAX_RATE_AWARE_CUSHION_MS = 60_000L
    }
}
