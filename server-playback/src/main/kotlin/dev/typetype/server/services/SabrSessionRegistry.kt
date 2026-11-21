package dev.typetype.server.services

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

class SabrSessionRegistry {
    private val sessions = ConcurrentHashMap<SabrSessionKey, SabrSessionHolder>()
    private val sessionsByToken = ConcurrentHashMap<String, SabrSessionHolder>()
    private val mutationLock = Any()
    private val logger = LoggerFactory.getLogger(SabrSessionRegistry::class.java)

    @Volatile
    private var lastSaturatedLogAt = 0L

    fun get(key: SabrSessionKey): SabrSessionHolder? {
        val holder = sessions[key]
        holder?.touch()
        return holder
    }

    fun getReusable(key: SabrSessionKey): SabrSessionHolder? {
        val holder = get(key) ?: return null
        if (holder.terminalFailure() == null && holder.playbackState() != SabrPlaybackState.NETWORK_FAILED) return holder
        remove(holder)
        return null
    }

    fun put(key: SabrSessionKey, holder: SabrSessionHolder): SabrSessionHolder {
        val active = synchronized(mutationLock) {
            sessions[key]?.also { it.touch() } ?: holder.also {
                sessions[key] = it
                sessionsByToken[it.sessionToken] = it
            }
        }
        if (active !== holder) holder.releaseResources()
        return active
    }

    fun contains(holder: SabrSessionHolder): Boolean = sessions[holder.key] === holder

    // A client that moves to another video releases its session at once instead of holding one of the few
    // registry slots until idle eviction, which matters when one viewer jumps through many videos.
    fun releaseByClient(holder: SabrSessionHolder): Boolean {
        val removed = synchronized(mutationLock) {
            if (!sessions.remove(holder.key, holder)) return@synchronized false
            sessionsByToken.remove(holder.sessionToken, holder)
            true
        }
        if (!removed) return false
        holder.releaseResources()
        logger.info(
            "sabr_session event=released videoId={} registrySize={} state={}",
            holder.key.videoId,
            sessions.size,
            holder.playbackState(),
        )
        return true
    }

    fun remove(holder: SabrSessionHolder): Unit {
        val removed = synchronized(mutationLock) {
            if (!sessions.remove(holder.key, holder)) return@synchronized false
            sessionsByToken.remove(holder.sessionToken, holder)
            true
        }
        if (removed) holder.releaseResources()
    }

    fun lookupByItag(videoId: String, userId: String, itag: Int): SabrSessionHolder? {
        val now = Instant.now()
        for ((key, holder) in sessions) {
            if (key.userId != userId) continue
            if (holder.info.videoId != videoId) continue
            if (holder.audioFormat.itag != itag && holder.videoFormat.itag != itag) continue
            holder.touch(now)
            return holder
        }
        return null
    }

    fun lookupByToken(videoId: String, token: String, itag: Int): SabrSessionHolder? {
        val holder = sessionsByToken[token] ?: return null
        if (holder.info.videoId != videoId) return null
        if (holder.audioFormat.itag != itag && holder.videoFormat.itag != itag) return null
        holder.touch()
        return holder
    }

    fun lookupByToken(videoId: String, token: String): SabrSessionHolder? {
        val holder = sessionsByToken[token] ?: return null
        if (holder.info.videoId != videoId) return null
        holder.touch()
        return holder
    }

    fun lookupByToken(token: String): SabrSessionHolder? {
        val holder = sessionsByToken[token] ?: return null
        holder.touch()
        return holder
    }

    /**
     * Makes room for one more session. A session a viewer is playing is never evicted for room: the
     * client would rebuild it at once, which is a reposition YouTube may answer with an attestation
     * demand. The count is a target while the bytes all sessions hold stay under [byteBudget].
     */
    fun ensureCapacity(
        maxSessions: Int,
        activeCutoff: Instant = Instant.now().minus(SabrSessionStoreDefaults.idleEviction()),
        playingCutoff: Instant = Instant.now().minus(SabrSessionStoreDefaults.playingWindow()),
        byteBudget: Long = SabrSessionStoreDefaults.byteBudget(),
    ) {
        while (sessions.size >= maxSessions) {
            val oldest = evictionCandidate(null, activeCutoff, playingCutoff, byteBudget)
                ?: return logCapacitySaturated(maxSessions)
            evict(oldest.key, "capacity")
        }
    }

    fun trimToCapacity(
        maxSessions: Int,
        protected: SabrSessionHolder,
        activeCutoff: Instant = Instant.now().minus(SabrSessionStoreDefaults.idleEviction()),
        playingCutoff: Instant = Instant.now().minus(SabrSessionStoreDefaults.playingWindow()),
        byteBudget: Long = SabrSessionStoreDefaults.byteBudget(),
    ) {
        while (sessions.size > maxSessions) {
            val oldest = evictionCandidate(protected, activeCutoff, playingCutoff, byteBudget)
                ?: return logCapacitySaturated(maxSessions)
            evict(oldest.key, "capacity")
        }
    }

    private fun evictionCandidate(
        protected: SabrSessionHolder?,
        activeCutoff: Instant,
        playingCutoff: Instant,
        byteBudget: Long,
    ): Map.Entry<SabrSessionKey, SabrSessionHolder>? {
        val others = sessions.entries.filterNot { it.value === protected || isActiveLive(it.value, activeCutoff) }
        others.filterNot { isPlaying(it.value, playingCutoff) }.minByOrNull { it.value.lastRequestAt }?.let { return it }
        if (heldBytes() <= byteBudget) return null
        return others.minByOrNull { it.value.lastRequestAt }
    }

    private fun heldBytes(): Long = sessions.values.sumOf { holder ->
        runCatching { holder.session.cachedBytes }.getOrDefault(0L)
    }

    fun evictIdle(cutoff: Instant) {
        val stale = sessions.entries
            .filter { it.value.lastRequestAt.isBefore(cutoff) && !isActiveLive(it.value, cutoff) }
            .map { it.key }
        stale.forEach { evict(it, "idle") }
    }

    fun clear() {
        val holders = synchronized(mutationLock) {
            sessions.values.toSet().also {
                sessions.clear()
                sessionsByToken.clear()
            }
        }
        SabrSegmentDemandTracker.clearAll()
        holders.forEach(SabrSessionHolder::releaseResources)
    }

    private fun remove(key: SabrSessionKey) {
        val holder = synchronized(mutationLock) {
            sessions.remove(key)?.also {
                sessionsByToken.remove(it.sessionToken, it)
            }
        }
        holder?.releaseResources()
    }

    private fun isActiveLive(holder: SabrSessionHolder, cutoff: Instant): Boolean =
        holder.expectsLive() && isServing(holder, cutoff)

    private fun isPlaying(holder: SabrSessionHolder, cutoff: Instant): Boolean =
        !holder.expectsLive() && isServing(holder, cutoff)

    private fun isServing(holder: SabrSessionHolder, cutoff: Instant): Boolean {
        if (holder.lastRequestAt.isBefore(cutoff)) return false
        return holder.playbackState() !in ENDED_STATES
    }

    private fun evict(key: SabrSessionKey, reason: String) {
        val now = Instant.now()
        val holder = synchronized(mutationLock) {
            sessions.remove(key)?.also { sessionsByToken.remove(it.sessionToken, it) }
        } ?: return
        val ageMs = java.time.Duration.between(holder.lastRequestAt, now).toMillis().coerceAtLeast(0L)
        holder.releaseResources()
        logger.info(
            "sabr_session event=evicted reason={} videoId={} ageMs={} registrySize={} liveSessions={} state={}",
            reason,
            holder.key.videoId,
            ageMs,
            sessions.size,
            sessions.values.count { it.expectsLive() },
            holder.playbackState(),
        )
    }

    private fun logCapacitySaturated(maxSessions: Int) {
        val now = System.currentTimeMillis()
        if (now - lastSaturatedLogAt < SATURATED_LOG_INTERVAL_MS) return
        lastSaturatedLogAt = now
        logger.warn(
            "sabr_session event=capacity_saturated maxSessions={} registrySize={} liveSessions={}",
            maxSessions,
            sessions.size,
            sessions.values.count { it.expectsLive() },
        )
    }

    private companion object {
        const val SATURATED_LOG_INTERVAL_MS = 60_000L
        val ENDED_STATES = setOf(
            SabrPlaybackState.NETWORK_FAILED,
            SabrPlaybackState.TERMINAL,
            SabrPlaybackState.STOPPED,
        )
    }
}
