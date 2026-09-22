package dev.typetype.server.services

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

class SabrSessionRegistry {
    private val sessions = ConcurrentHashMap<SabrSessionKey, SabrSessionHolder>()
    private val sessionsByToken = ConcurrentHashMap<String, SabrSessionHolder>()
    private val mutationLock = Any()
    private val logger = LoggerFactory.getLogger(SabrSessionRegistry::class.java)

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

    fun ensureCapacity(
        maxSessions: Int,
        activeCutoff: Instant = Instant.now().minus(SabrSessionStoreDefaults.idleEviction()),
    ) {
        while (sessions.size >= maxSessions) {
            val oldest = sessions.entries
                .asSequence()
                .filterNot { isActiveLive(it.value, activeCutoff) }
                .minByOrNull { it.value.lastRequestAt }
            if (oldest == null) {
                logCapacitySaturated(maxSessions)
                return
            }
            evict(oldest.key, "capacity")
        }
    }

    fun trimToCapacity(
        maxSessions: Int,
        protected: SabrSessionHolder,
        activeCutoff: Instant = Instant.now().minus(SabrSessionStoreDefaults.idleEviction()),
    ) {
        while (sessions.size > maxSessions) {
            val oldest = sessions.entries
                .asSequence()
                .filterNot { it.value === protected }
                .filterNot { isActiveLive(it.value, activeCutoff) }
                .minByOrNull { it.value.lastRequestAt }
            if (oldest == null) {
                logCapacitySaturated(maxSessions)
                return
            }
            evict(oldest.key, "capacity")
        }
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

    private fun isActiveLive(holder: SabrSessionHolder, cutoff: Instant): Boolean {
        if (!holder.expectsLive() || holder.lastRequestAt.isBefore(cutoff)) return false
        return holder.playbackState() !in setOf(
            SabrPlaybackState.NETWORK_FAILED,
            SabrPlaybackState.TERMINAL,
            SabrPlaybackState.STOPPED,
        )
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
        logger.warn(
            "sabr_session event=capacity_saturated maxSessions={} registrySize={} liveSessions={}",
            maxSessions,
            sessions.size,
            sessions.values.count { it.expectsLive() },
        )
    }
}
