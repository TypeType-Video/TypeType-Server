package dev.typetype.server.routes

import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.awaitPlaybackProgress
import dev.typetype.server.services.livePlaybackSnapshot
import dev.typetype.server.services.playbackProgressVersion

internal class SabrPlaybackWindowAwaiter(
    private val maxWaitMs: Long = MAX_WAIT_MS,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    suspend fun await(
        holder: SabrSessionHolder,
        initial: SabrPlaybackWindowBuildResult,
        build: suspend () -> SabrPlaybackWindowBuildResult,
    ): SabrPlaybackWindowBuildResult {
        if (initial.isReady || !holder.waitsForWindow()) return initial
        val deadline = nowMs() + maxWaitMs
        var window = initial
        while (!window.isReady && holder.waitsForWindow()) {
            val version = holder.playbackProgressVersion()
            window = build()
            val remainingMs = deadline - nowMs()
            if (window.isReady || remainingMs <= 0L) break
            holder.awaitPlaybackProgress(version, remainingMs)
        }
        return window
    }

    private fun SabrSessionHolder.waitsForWindow(): Boolean =
        !expectsLive() && livePlaybackSnapshot()?.active != true &&
            terminalFailure() == null && networkFailure() == null

    private companion object {
        const val MAX_WAIT_MS = 1_500L
    }
}
