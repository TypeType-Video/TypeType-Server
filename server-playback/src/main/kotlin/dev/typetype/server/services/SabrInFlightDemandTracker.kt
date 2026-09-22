package dev.typetype.server.services

import dev.typetype.server.sabr.SabrSegmentRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal enum class SabrDemandInterruptionAction {
    WAIT_FOR_LIVE,
    RESTART,
}

internal data class SabrDemandInterruption(
    val action: SabrDemandInterruptionAction,
    val atMs: Long,
    val reason: String,
)

class SabrInFlightDemand(
    val request: SabrSegmentRequest,
    val identity: String,
    val registeredAtMs: Long,
    val futureLiveRequest: Boolean,
) {
    private var lastProgressVersion = Long.MIN_VALUE
    private var lastProgressAtMs = registeredAtMs
    private var attemptCancellation: (() -> Unit)? = null
    private var interruption: SabrDemandInterruption? = null
    private val attemptCount = AtomicInteger(0)
    @Volatile private var attemptStartedAtNanos = 0L
    @Volatile private var lastAttemptDurationMs = -1L

    fun observeProgress(version: Long, observedAtMs: Long): Long {
        if (version != lastProgressVersion) {
            lastProgressVersion = version
            lastProgressAtMs = observedAtMs
        }
        return lastProgressAtMs
    }

    fun registerAttemptCancellation(cancel: () -> Unit): Unit {
        val cancelNow = synchronized(this) {
            if (interruption != null) true else {
                attemptCancellation = cancel
                false
            }
        }
        if (cancelNow) cancel()
    }

    internal fun requestInterruption(next: SabrDemandInterruption): Boolean {
        val callback = synchronized(this) {
            if (interruption != null) return@synchronized null
            interruption = next
            val pending = attemptCancellation
            attemptCancellation = null
            pending
        }
        callback?.invoke()
        return true
    }

    internal fun consumeInterruption(): SabrDemandInterruption? = synchronized(this) {
        interruption.also { interruption = null }
    }

    fun clearAttemptCancellation(): Unit = synchronized(this) {
        attemptCancellation = null
    }

    fun recordAttemptStarted(): Unit {
        attemptCount.incrementAndGet()
        attemptStartedAtNanos = System.nanoTime()
    }

    fun recordAttemptFinished(): Unit {
        val startedAt = attemptStartedAtNanos
        if (startedAt > 0L) {
            lastAttemptDurationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        }
        attemptStartedAtNanos = 0L
    }

    fun attempts(): Int = attemptCount.get()

    fun lastAttemptDurationMs(): Long = lastAttemptDurationMs
}

object SabrInFlightDemandTracker {
    private val demands = ConcurrentHashMap<String, SabrInFlightDemand>()

    fun begin(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        identity: String,
        futureLiveRequest: Boolean,
    ): Boolean {
        val registeredAtMs = holder.segmentDemandRegisteredAtMs(request, identity) ?: return false
        val demand = SabrInFlightDemand(
            request,
            identity,
            registeredAtMs,
            futureLiveRequest,
        )
        return demands.putIfAbsent(holder.sessionToken, demand) == null
    }

    fun current(holder: SabrSessionHolder): SabrInFlightDemand? = demands[holder.sessionToken]

    fun finish(holder: SabrSessionHolder, identity: String): Boolean {
        val demand = demands[holder.sessionToken] ?: return false
        if (demand.identity != identity) return false
        return demands.remove(holder.sessionToken, demand)
    }

    fun clear(holder: SabrSessionHolder): Unit {
        demands.remove(holder.sessionToken)
    }

    fun clearAll(): Unit = demands.clear()
}

fun SabrSessionHolder.beginInFlightSegmentDemand(
    request: SabrSegmentRequest,
    identity: String,
    futureLiveRequest: Boolean,
): Boolean = SabrInFlightDemandTracker.begin(this, request, identity, futureLiveRequest)

fun SabrSessionHolder.inFlightSegmentDemand(): SabrInFlightDemand? =
    SabrInFlightDemandTracker.current(this)

fun SabrSessionHolder.finishInFlightSegmentDemand(identity: String): Boolean =
    SabrInFlightDemandTracker.finish(this, identity)

fun SabrSessionHolder.clearInFlightSegmentDemand(): Unit =
    SabrInFlightDemandTracker.clear(this)

internal fun SabrSessionHolder.registerInFlightDemandCancellation(
    identity: String,
    cancel: () -> Unit,
): Boolean = SabrInFlightDemandTracker.current(this)
    ?.takeIf { it.identity == identity }
    ?.let { it.registerAttemptCancellation(cancel); true }
    ?: false

internal fun SabrSessionHolder.requestInFlightDemandInterruption(
    identity: String,
    interruption: SabrDemandInterruption,
): Boolean = SabrInFlightDemandTracker.current(this)
    ?.takeIf { it.identity == identity }
    ?.requestInterruption(interruption)
    ?: false

internal fun SabrSessionHolder.consumeInFlightDemandInterruption(identity: String): SabrDemandInterruption? =
    SabrInFlightDemandTracker.current(this)
        ?.takeIf { it.identity == identity }
        ?.consumeInterruption()

internal fun SabrSessionHolder.clearInFlightDemandCancellation(identity: String): Unit {
    SabrInFlightDemandTracker.current(this)
        ?.takeIf { it.identity == identity }
        ?.clearAttemptCancellation()
}

internal fun SabrSessionHolder.recordInFlightAttemptStarted(): Unit {
    SabrInFlightDemandTracker.current(this)?.recordAttemptStarted()
}

internal fun SabrSessionHolder.recordInFlightAttemptFinished(): Unit {
    SabrInFlightDemandTracker.current(this)?.recordAttemptFinished()
}
