package dev.typetype.server.services

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class SabrPumpCoordinator {
    private val started = AtomicBoolean(false)
    private val wakeVersion = AtomicLong()
    private val wakeups = Channel<Unit>(Channel.CONFLATED)
    private val progress = MutableStateFlow(0L)

    fun markStarted(): Boolean = started.compareAndSet(false, true)

    fun markStopped(): Unit = started.set(false)

    fun wakeVersion(): Long = wakeVersion.get()

    fun wake(): Unit {
        wakeVersion.incrementAndGet()
        wakeups.trySend(Unit)
    }

    fun progressVersion(): Long = progress.value

    fun signalProgress(): Unit = progress.update { it + 1 }

    suspend fun awaitProgress(observedVersion: Long, timeoutMs: Long): Unit {
        if (timeoutMs <= 0L) return
        withTimeoutOrNull(timeoutMs) { progress.first { it != observedVersion } }
    }

    suspend fun awaitWake(observedVersion: Long, timeoutMs: Long): Unit {
        if (timeoutMs <= 0L) return
        if (wakeVersion.get() != observedVersion) return
        withTimeoutOrNull(timeoutMs) {
            while (wakeVersion.get() == observedVersion) wakeups.receive()
        }
    }
}

fun SabrSessionHolder.markPumpStarted(): Boolean = pumpCoordinator.markStarted()

fun SabrSessionHolder.markPumpStopped(): Unit = pumpCoordinator.markStopped()

fun SabrSessionHolder.wakePump(): Unit = pumpCoordinator.wake()

fun SabrSessionHolder.pumpWakeVersion(): Long = pumpCoordinator.wakeVersion()

internal suspend fun SabrSessionHolder.awaitPumpWake(observedVersion: Long, timeoutMs: Long): Unit =
    pumpCoordinator.awaitWake(observedVersion, timeoutMs)

fun SabrSessionHolder.playbackProgressVersion(): Long = pumpCoordinator.progressVersion()

fun SabrSessionHolder.signalPlaybackProgress(): Unit = pumpCoordinator.signalProgress()

suspend fun SabrSessionHolder.awaitPlaybackProgress(observedVersion: Long, timeoutMs: Long): Unit =
    pumpCoordinator.awaitProgress(observedVersion, timeoutMs)
