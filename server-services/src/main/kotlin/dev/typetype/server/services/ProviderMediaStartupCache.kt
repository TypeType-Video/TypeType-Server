package dev.typetype.server.services

import dev.typetype.server.models.ProxyResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap

class ProviderMediaStartupCache(
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    class Entry(
        val bytes: ByteArray,
        val totalLength: Long?,
        val contentType: String,
        val complete: Boolean,
        val offset: Long = 0,
    )

    private class Stored(val entry: Entry, val storedAt: Long)

    private val entries = LinkedHashMap<String, Stored>(64, 0.75f, true)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private var storedBytes = 0L

    fun begin(handle: String, extending: Boolean = false): CompletableDeferred<Unit>? {
        if (!extending && get(handle) != null) return null
        val claim = CompletableDeferred<Unit>()
        return if (pending.putIfAbsent(handle, claim) == null) claim else null
    }

    fun finish(handle: String, claim: CompletableDeferred<Unit>) {
        pending.remove(handle, claim)
        claim.complete(Unit)
    }

    suspend fun awaitPending(handle: String, timeoutMillis: Long = PENDING_WAIT_MILLIS) {
        pending[handle]?.let { withTimeoutOrNull(timeoutMillis) { it.await() } }
    }

    @Synchronized
    fun put(handle: String, entry: Entry) {
        if (entry.bytes.size > maxBytes) return
        entries.remove(handle)?.let { storedBytes -= it.entry.bytes.size }
        entries[handle] = Stored(entry, nowMillis())
        storedBytes += entry.bytes.size
        val iterator = entries.entries.iterator()
        while (storedBytes > maxBytes && iterator.hasNext()) {
            storedBytes -= iterator.next().value.entry.bytes.size
            iterator.remove()
        }
    }

    @Synchronized
    fun get(handle: String): Entry? {
        val stored = entries[handle] ?: return null
        if (nowMillis() - stored.storedAt > ttlMillis) {
            entries.remove(handle)
            storedBytes -= stored.entry.bytes.size
            return null
        }
        return stored.entry
    }

    fun respond(url: String, rangeHeader: String?): ProxyResponse? {
        get(url)?.let { entry -> respondFrom(entry, rangeHeader)?.let { return it } }
        val start = rangeStart(rangeHeader) ?: return null
        return get(segmentKey(url, start))?.let { respondFrom(it, rangeHeader) }
    }

    private fun respondFrom(entry: Entry, rangeHeader: String?): ProxyResponse? {
        if (rangeHeader == null) {
            return if (entry.complete && entry.offset == 0L) response(entry, 200, entry.bytes, null) else null
        }
        val range = parseRange(rangeHeader) ?: return null
        val end = entry.offset + entry.bytes.size - 1
        val last = range.last ?: (if (entry.complete) end else return null)
        if (range.first < entry.offset || range.first > last || last > end) return null
        val total = entry.totalLength?.toString() ?: "*"
        val slice = entry.bytes.copyOfRange((range.first - entry.offset).toInt(), (last - entry.offset).toInt() + 1)
        return response(entry, 206, slice, "bytes ${range.first}-$last/$total")
    }

    private fun rangeStart(rangeHeader: String?): Long? = rangeHeader?.let(::parseRange)?.first

    private fun response(entry: Entry, status: Int, body: ByteArray, contentRange: String?) = ProxyResponse(
        status = status,
        contentType = entry.contentType,
        contentLength = body.size.toLong(),
        contentRange = contentRange,
        acceptRanges = "bytes",
        stream = ByteArrayInputStream(body),
        close = {},
    )

    private class ByteRange(val first: Long, val last: Long?)

    private fun parseRange(header: String): ByteRange? {
        val match = RANGE.matchEntire(header.trim()) ?: return null
        val first = match.groupValues[1].toLongOrNull() ?: return null
        val last = match.groupValues[2].takeIf { it.isNotEmpty() }?.toLongOrNull()
        return ByteRange(first, last)
    }

    companion object {
        fun segmentKey(url: String, start: Long): String = "$url#$start"

        const val DEFAULT_MAX_BYTES = 48L * 1024 * 1024
        const val DEFAULT_TTL_MILLIS = 120_000L
        const val PENDING_WAIT_MILLIS = 4_000L
        private val RANGE = Regex("bytes=(\\d+)-(\\d*)")
    }
}
