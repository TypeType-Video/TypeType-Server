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
    )

    private class Stored(val entry: Entry, val storedAt: Long)

    private val entries = LinkedHashMap<String, Stored>(64, 0.75f, true)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private var storedBytes = 0L

    fun begin(handle: String): CompletableDeferred<Unit>? {
        if (get(handle) != null) return null
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

    fun respond(handle: String, rangeHeader: String?): ProxyResponse? {
        val entry = get(handle) ?: return null
        if (rangeHeader == null) {
            return if (entry.complete) response(entry, 200, entry.bytes, null) else null
        }
        val range = parseRange(rangeHeader) ?: return null
        val last = range.last ?: (if (entry.complete) entry.bytes.size - 1L else return null)
        if (range.first > last || last >= entry.bytes.size) return null
        val total = entry.totalLength?.toString() ?: "*"
        val slice = entry.bytes.copyOfRange(range.first.toInt(), last.toInt() + 1)
        return response(entry, 206, slice, "bytes ${range.first}-$last/$total")
    }

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
        const val DEFAULT_MAX_BYTES = 48L * 1024 * 1024
        const val DEFAULT_TTL_MILLIS = 120_000L
        const val PENDING_WAIT_MILLIS = 4_000L
        private val RANGE = Regex("bytes=(\\d+)-(\\d*)")
    }
}
