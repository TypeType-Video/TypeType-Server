package dev.typetype.server.services

import dev.typetype.server.sabr.SabrSegmentRequest
import java.util.concurrent.ConcurrentHashMap

class SabrInFlightDemand(
    val request: SabrSegmentRequest,
    val identity: String,
    val registeredAtMs: Long,
    val futureLiveRequest: Boolean,
) {
    private var lastProgressVersion = Long.MIN_VALUE
    private var lastProgressAtMs = registeredAtMs

    fun observeProgress(version: Long, observedAtMs: Long): Long {
        if (version != lastProgressVersion) {
            lastProgressVersion = version
            lastProgressAtMs = observedAtMs
        }
        return lastProgressAtMs
    }
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
