package dev.typetype.server.services

fun SabrSessionHolder.releaseResources() {
    clearSegmentDemands()
    clearInFlightSegmentDemand()
    SabrPlaybackDiagnostics.clear(this)
    session.clearCache()
}
