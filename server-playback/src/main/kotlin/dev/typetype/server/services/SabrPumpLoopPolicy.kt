package dev.typetype.server.services

fun preparePumpEviction(holder: SabrSessionHolder) {
    val retainedPlayHeadMs =
        (holder.readerTailMs() - SabrPumpPolicy.backBufferMs(holder)).coerceAtLeast(0L)
    holder.session.setPlayHeadMs(retainedPlayHeadMs)
    holder.session.evictPlayed()
}

fun pumpDemandDelayMs(holder: SabrSessionHolder, intervalMs: Long): Long {
    val demand = holder.nextSegmentDemand()
    return SabrPumpPolicy.demandDelayMs(
        intervalMs,
        holder.session.demandBackoffRemainingMs.takeIf { demand != null } ?: 0L,
        holder.livePlaybackSnapshot()?.active == true,
        demand?.let(holder::isFutureLiveRequest),
    )
}
