package dev.typetype.server.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

fun CoroutineScope.launchSabrPump(
    pump: SabrSessionPump,
    registry: SabrSessionRegistry,
    holder: SabrSessionHolder,
    intervalMs: Long,
    watchdog: SabrDemandWatchdog = SabrDemandWatchdog(),
): Unit {
    val state = holder.playbackState()
    if (state == SabrPlaybackState.TERMINAL || state == SabrPlaybackState.NETWORK_FAILED) return
    if (!holder.markPumpStarted()) {
        holder.wakePump()
        return
    }
    launch {
        val owner = currentCoroutineContext().job
        val watchdogJob = launch {
            if (watchdog.monitor({ registry.contains(holder) }, holder)) owner.cancel()
        }
        try {
            pump.pumpLoop({ registry.contains(holder) }, holder, intervalMs)
        } finally {
            watchdogJob.cancel()
            holder.markPumpStopped()
        }
    }
}
