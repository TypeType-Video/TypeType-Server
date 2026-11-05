package dev.typetype.server.services

class ProviderMediaReadAhead(
    private val warmup: ProviderMediaWarmup,
    private val segments: ProviderMediaSegmentTable,
) {
    fun onServed(handle: String, target: ProviderMediaTarget, rangeHeader: String?) {
        if (rangeHeader == null) {
            warmup.prefetchFollowing(handle)
            return
        }
        val start = rangeHeader?.let { RANGE_START.matchEntire(it.trim()) }?.groupValues?.get(1)?.toLongOrNull() ?: return
        val next = segments.after(target.url, start) ?: return
        if (next.last - next.first + 1 > ProviderMediaWarmup.MAX_SEGMENT_BYTES) return
        warmup.prefetchSegment(target, next)
    }

    private companion object {
        val RANGE_START = Regex("bytes=(\\d+)-\\d*")
    }
}
