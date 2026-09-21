package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.models.ActiveSessionItem
import dev.typetype.server.models.SessionActivityRequest
import dev.typetype.server.models.SessionPlaybackProgressRequest
import dev.typetype.server.models.SessionPlaybackStartRequest
import dev.typetype.server.models.SessionPlaybackStopRequest
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.concurrent.ConcurrentHashMap

class ActiveSessionService(
    private val adminSettingsService: AdminSettingsService,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) {
    private val sessions = ConcurrentHashMap<String, ActiveSessionRecord>()

    suspend fun reportActivity(userId: String, request: SessionActivityRequest, userAgent: String?): Unit {
        updateSession(userId, request.clientName, request.clientVersion, request.deviceId, request.deviceName, request.deviceType, userAgent, null, null)
    }

    suspend fun reportPlaybackStart(userId: String, request: SessionPlaybackStartRequest, userAgent: String?): Unit {
        val now = nowProvider()
        val nowPlaying = ActiveSessionNowPlayingMapper.fromStart(request, now)
        updateSession(userId, request.clientName, request.clientVersion, request.deviceId, request.deviceName, request.deviceType, userAgent, nowPlaying, now)
    }

    suspend fun reportPlaybackProgress(userId: String, request: SessionPlaybackProgressRequest, userAgent: String?): Unit {
        val now = nowProvider()
        val id = sessionId(userId, request.deviceId, request.clientName)
        val current = sessions[id]?.nowPlaying
        val nowPlaying = ActiveSessionNowPlayingMapper.fromProgress(current, request, now)
        updateSession(userId, request.clientName, request.clientVersion, request.deviceId, request.deviceName, request.deviceType, userAgent, nowPlaying, now)
    }

    suspend fun reportPlaybackStop(userId: String, request: SessionPlaybackStopRequest, userAgent: String?): Unit {
        updateSession(userId, request.clientName, request.clientVersion, request.deviceId, request.deviceName, request.deviceType, userAgent, null, nowProvider())
    }

    suspend fun list(): List<ActiveSessionItem> {
        if (!active()) return emptyList()
        val now = nowProvider()
        pruneExpired(now)
        return sessions.values.sortedByDescending { it.lastActivityAt }.map { it.toItem() }
    }

    fun clear(): Unit {
        sessions.clear()
    }

    private suspend fun updateSession(
        userId: String,
        clientName: String?,
        clientVersion: String?,
        deviceId: String?,
        deviceName: String?,
        deviceType: String?,
        userAgent: String?,
        nowPlaying: dev.typetype.server.models.ActiveSessionNowPlayingItem?,
        lastPlaybackAt: Long?,
    ) {
        if (!active()) return
        val now = nowProvider()
        pruneExpired(now)
        val id = sessionId(userId, deviceId, clientName)
        sessions[id] = ActiveSessionRecord(
            id = id,
            userId = userId,
            username = username(userId),
            clientName = ActiveSessionStrings.text(clientName),
            clientVersion = ActiveSessionStrings.text(clientVersion),
            deviceId = ActiveSessionStrings.text(deviceId),
            deviceName = ActiveSessionStrings.text(deviceName),
            deviceType = ActiveSessionStrings.text(deviceType),
            userAgent = ActiveSessionStrings.userAgent(userAgent),
            lastActivityAt = now,
            lastPlaybackAt = lastPlaybackAt,
            nowPlaying = nowPlaying,
        )
    }

    private suspend fun active(): Boolean {
        val enabled = adminSettingsService.get().activeSessionsEnabled
        if (!enabled) clear()
        return enabled
    }

    private fun pruneExpired(now: Long) {
        sessions.entries.removeIf { now - it.value.lastActivityAt > INACTIVITY_TTL_MS }
    }

    private suspend fun username(userId: String): String? {
        if (userId.startsWith("guest:")) return null
        return DatabaseFactory.query {
            UsersTable.selectAll().where { UsersTable.id eq userId }.singleOrNull()?.let {
                it[UsersTable.publicUsername] ?: it[UsersTable.name]
            }
        }
    }

    private fun sessionId(userId: String, deviceId: String?, clientName: String?): String =
        listOf(userId, ActiveSessionStrings.key(deviceId), ActiveSessionStrings.key(clientName)).joinToString(":")

    private fun ActiveSessionRecord.toItem(): ActiveSessionItem = ActiveSessionItem(id, userId, username, clientName, clientVersion, deviceId, deviceName, deviceType, userAgent, null, lastActivityAt, lastPlaybackAt, nowPlaying)

    companion object {
        const val INACTIVITY_TTL_MS = 120_000L
    }
}
