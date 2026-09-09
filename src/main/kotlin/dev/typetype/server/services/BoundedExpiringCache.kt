package dev.typetype.server.services

import java.time.Duration
import java.util.LinkedHashMap

internal class BoundedExpiringCache<K, V>(
    private val maxEntries: Int,
    private val maxWeight: Long = Long.MAX_VALUE,
    ttl: Duration,
    private val weigher: (V) -> Long = { 1L },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val ttlMs = ttl.toMillis()
    private val entries = LinkedHashMap<K, Entry<V>>(maxEntries.coerceAtMost(64), 0.75f, true)
    private var weight = 0L
    // Removals may leave an earlier bound, causing one extra scan but never delaying expiry.
    private var nextExpiryMs = Long.MAX_VALUE

    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
        require(maxWeight > 0L) { "maxWeight must be positive" }
        require(!ttl.isNegative && !ttl.isZero) { "ttl must be positive" }
    }

    @Synchronized
    fun get(key: K): V? {
        evictExpired(clock())
        return entries[key]?.value
    }

    @Synchronized
    fun put(key: K, value: V) {
        val now = clock()
        evictExpired(now)
        removeEntry(key)
        val entryWeight = weigher(value).coerceAtLeast(0L)
        if (entryWeight > maxWeight) return
        val expiry = expiresAt(now)
        entries[key] = Entry(value, expiry, entryWeight)
        nextExpiryMs = minOf(nextExpiryMs, expiry)
        weight += entryWeight
        trim()
    }

    @Synchronized
    fun remove(key: K): V? = removeEntry(key)?.value

    @Synchronized
    fun removeIf(predicate: (K) -> Boolean) {
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!predicate(entry.key)) continue
            weight -= entry.value.weight
            iterator.remove()
        }
    }

    @Synchronized
    fun evictExpired() {
        evictExpired(clock())
    }

    @Synchronized
    fun clear() {
        entries.clear()
        weight = 0L
        nextExpiryMs = Long.MAX_VALUE
    }

    @Synchronized
    internal fun size(): Int = entries.size

    @Synchronized
    internal fun weight(): Long = weight

    private fun expiresAt(now: Long): Long =
        if (Long.MAX_VALUE - now < ttlMs) Long.MAX_VALUE else now + ttlMs

    private fun evictExpired(now: Long) {
        if (now < nextExpiryMs) return
        nextExpiryMs = Long.MAX_VALUE
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            if (entry.expiresAtMs > now) {
                nextExpiryMs = minOf(nextExpiryMs, entry.expiresAtMs)
                continue
            }
            weight -= entry.weight
            iterator.remove()
        }
    }

    private fun trim() {
        val iterator = entries.iterator()
        while ((entries.size > maxEntries || weight > maxWeight) && iterator.hasNext()) {
            weight -= iterator.next().value.weight
            iterator.remove()
        }
    }

    private fun removeEntry(key: K): Entry<V>? =
        entries.remove(key)?.also { weight -= it.weight }

    private data class Entry<V>(val value: V, val expiresAtMs: Long, val weight: Long)
}
