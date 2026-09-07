package dev.typetype.server.services

import dev.typetype.server.sabr.SabrSegmentRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal object SabrSegmentDemandTracker {
    private val demands = ConcurrentHashMap<String, SegmentDemand>()
    private val order = AtomicLong()

    fun request(holder: SabrSessionHolder, request: SabrSegmentRequest, registeredAtMs: Long): Unit {
        if (request.isInitializationSegment) return
        if (holder.session.getCachedSegment(request) != null) return clear(holder, request)
        if (holder.hasFiniteEndBefore(request)) return clear(holder, request)
        val requestKey = key(holder, request)
        demands.putIfAbsent(requestKey, SegmentDemand(request, order.incrementAndGet(), registeredAtMs))
    }

    fun clear(holder: SabrSessionHolder, request: SabrSegmentRequest): Unit {
        demands.remove(key(holder, request))
    }

    fun clear(holder: SabrSessionHolder): Unit {
        val prefix = holderPrefix(holder)
        demands.keys.removeIf { it.startsWith(prefix) }
    }

    fun clearAll(): Unit {
        demands.clear()
    }

    fun next(holder: SabrSessionHolder): SabrSegmentRequest? {
        val prefix = prefix(holder)
        var selected: SabrSegmentRequest? = null
        var selectedStartMs = Long.MAX_VALUE
        var selectedOrder = Long.MAX_VALUE
        for ((key, value) in demands) {
            if (!key.startsWith(prefix)) continue
            val request = value.request
            if (holder.session.getCachedSegment(request) != null || holder.hasFiniteEndBefore(request)) {
                demands.remove(key, value)
                continue
            }
            val startMs = if (holder.isFutureLiveRequest(request)) {
                holder.livePlaybackSnapshot()?.seekableEndMs ?: Long.MAX_VALUE
            } else {
                holder.playbackSegmentStartMs(request.format, request.sequenceNumber)
            }
            if (startMs < selectedStartMs || startMs == selectedStartMs && value.order < selectedOrder) {
                selected = request
                selectedStartMs = startMs
                selectedOrder = value.order
            }
        }
        return selected
    }

    fun identity(holder: SabrSessionHolder, request: SabrSegmentRequest): String? {
        val requestKey = key(holder, request)
        return demands[requestKey]?.let { identity(requestKey, it) }
    }

    fun isActive(holder: SabrSessionHolder, request: SabrSegmentRequest, identity: String): Boolean {
        val requestKey = key(holder, request)
        val demand = demands[requestKey] ?: return false
        return identity(requestKey, demand) == identity
    }

    fun registeredAtMs(holder: SabrSessionHolder, request: SabrSegmentRequest, identity: String): Long? {
        val requestKey = key(holder, request)
        val demand = demands[requestKey] ?: return null
        return demand.registeredAtMs.takeIf { identity(requestKey, demand) == identity }
    }

    fun clear(holder: SabrSessionHolder, request: SabrSegmentRequest, identity: String): Boolean {
        val requestKey = key(holder, request)
        val demand = demands[requestKey] ?: return false
        if (identity(requestKey, demand) != identity) return false
        return demands.remove(requestKey, demand)
    }

    fun pendingSummary(holder: SabrSessionHolder): String? = next(holder)?.let { "${it.format.itag}:${it.sequenceNumber}" }

    private fun key(holder: SabrSessionHolder, request: SabrSegmentRequest): String =
        "${prefix(holder)}${request.format.itag}:${request.sequenceNumber}"

    private fun prefix(holder: SabrSessionHolder): String = "${holderPrefix(holder)}${holder.activeGeneration()}:"

    private fun holderPrefix(holder: SabrSessionHolder): String = "${holder.sessionToken}:"

    private fun identity(requestKey: String, demand: SegmentDemand): String = "$requestKey:${demand.order}"

    private fun SabrSessionHolder.hasFiniteEndBefore(request: SabrSegmentRequest): Boolean =
        session.isBeyondEnd(request) && livePlaybackSnapshot()?.active != true

    private data class SegmentDemand(
        val request: SabrSegmentRequest,
        val order: Long,
        val registeredAtMs: Long,
    )
}

internal fun SabrSessionHolder.requestSegmentDemand(
    request: SabrSegmentRequest,
    generation: Long = activeGeneration(),
    registeredAtMs: Long = System.currentTimeMillis(),
): Unit = synchronized(this) {
    val state = playbackState()
    if (generation == activeGeneration() && state != SabrPlaybackState.TERMINAL && state != SabrPlaybackState.NETWORK_FAILED) {
        SabrSegmentDemandTracker.request(this, request, registeredAtMs)
    }
}

internal fun SabrSessionHolder.clearSegmentDemand(request: SabrSegmentRequest): Unit =
    SabrSegmentDemandTracker.clear(this, request)

internal fun SabrSessionHolder.clearSegmentDemands(): Unit = SabrSegmentDemandTracker.clear(this)

internal fun SabrSessionHolder.nextSegmentDemand(): SabrSegmentRequest? = SabrSegmentDemandTracker.next(this)

internal fun SabrSessionHolder.segmentDemandIdentity(request: SabrSegmentRequest): String? =
    SabrSegmentDemandTracker.identity(this, request)

internal fun SabrSessionHolder.isSegmentDemandActive(request: SabrSegmentRequest, identity: String): Boolean =
    SabrSegmentDemandTracker.isActive(this, request, identity)

internal fun SabrSessionHolder.segmentDemandRegisteredAtMs(request: SabrSegmentRequest, identity: String): Long? =
    SabrSegmentDemandTracker.registeredAtMs(this, request, identity)

internal fun SabrSessionHolder.clearSegmentDemand(request: SabrSegmentRequest, identity: String): Boolean =
    SabrSegmentDemandTracker.clear(this, request, identity)

internal fun SabrSessionHolder.pendingSegmentDemandSummary(): String? = SabrSegmentDemandTracker.pendingSummary(this)
