package dev.typetype.server.services

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

class YoutubeRemoteBrowserSessionStore(
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
) {
    private val sessions = mutableMapOf<String, YoutubeRemoteBrowserSession>()

    fun reserve(userId: String, config: YoutubeRemoteBrowserConfig): YoutubeRemoteBrowserReserveResult =
        synchronized(sessions) {
            cleanupExpiredLocked()
            if (sessions.values.any { it.userId == userId }) return@synchronized YoutubeRemoteBrowserReserveResult.AlreadyActive
            if (sessions.size >= config.maxGlobalSessions) return@synchronized YoutubeRemoteBrowserReserveResult.CapacityReached
            val token = randomToken()
            val session = YoutubeRemoteBrowserSession(
                sessionId = UUID.randomUUID().toString(),
                userId = userId,
                wsTokenHash = hash(token),
                tokenSessionId = null,
                expiresAt = nowMillis() + config.ttlMs,
            )
            sessions[session.sessionId] = session
            YoutubeRemoteBrowserReserveResult.Reserved(session, token)
        }

    fun attachTokenSession(sessionId: String, tokenSessionId: String, expiresAt: Long): YoutubeRemoteBrowserSession? =
        synchronized(sessions) {
            val session = sessions[sessionId]?.takeIf { it.expiresAt > nowMillis() } ?: return@synchronized null
            val attached = session.copy(tokenSessionId = tokenSessionId, expiresAt = minOf(session.expiresAt, expiresAt))
            sessions[sessionId] = attached
            attached
        }

    fun authenticateWebSocket(sessionId: String, token: String): YoutubeRemoteBrowserSession? =
        synchronized(sessions) {
            cleanupExpiredLocked()
            sessions[sessionId]?.takeIf { constantEquals(it.wsTokenHash, hash(token)) }
        }

    fun cancel(userId: String, sessionId: String): String? =
        synchronized(sessions) {
            val session = sessions[sessionId]?.takeIf { it.userId == userId } ?: return@synchronized null
            sessions.remove(sessionId)
            session.tokenSessionId
        }

    fun remove(sessionId: String): String? =
        synchronized(sessions) { sessions.remove(sessionId)?.tokenSessionId }

    fun complete(sessionId: String, tokenSessionId: String): YoutubeRemoteBrowserSession? =
        synchronized(sessions) {
            val session = sessions[sessionId]?.takeIf {
                it.tokenSessionId == tokenSessionId && it.expiresAt > nowMillis()
            } ?: return@synchronized null
            sessions.remove(sessionId)
            session
        }

    fun activeCount(): Int = synchronized(sessions) {
        cleanupExpiredLocked()
        sessions.size
    }

    private fun cleanupExpiredLocked() {
        val now = nowMillis()
        sessions.entries.removeIf { it.value.expiresAt <= now }
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hash(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        )

    private fun constantEquals(left: String, right: String): Boolean {
        if (left.length != right.length) return false
        var diff = 0
        left.indices.forEach { diff = diff or (left[it].code xor right[it].code) }
        return diff == 0
    }
}
