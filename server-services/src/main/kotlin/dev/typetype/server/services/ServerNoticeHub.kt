package dev.typetype.server.services

import dev.typetype.server.models.ServerHello
import dev.typetype.server.models.ServerNotice
import dev.typetype.server.models.ServerNoticeRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onSubscription
import java.util.UUID

sealed interface ServerEvent {
    data class Hello(val hello: ServerHello) : ServerEvent
    data class Notice(val notice: ServerNotice) : ServerEvent
    data object Cleared : ServerEvent
    data object Closed : ServerEvent
}

class ServerNoticeHub(
    private val bootId: String = UUID.randomUUID().toString(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = EVENT_BUFFER)

    @Volatile
    private var current: ServerNotice? = null

    val subscribers: StateFlow<Int> get() = events.subscriptionCount

    fun hello(): ServerHello = ServerHello(bootId, current)

    fun subscribe(): Flow<ServerEvent> = events.onSubscription { emit(ServerEvent.Hello(hello())) }

    fun announce(request: ServerNoticeRequest): ServerNotice? {
        val kind = request.kind.trim().lowercase()
        val message = request.message?.trim()?.takeIf { it.isNotEmpty() }
        val resume = request.resumeInSeconds
        if (kind !in KINDS) return null
        if (message != null && message.length > MAX_MESSAGE_LENGTH) return null
        if (resume != null && resume !in 0..MAX_RESUME_SECONDS) return null
        val notice = ServerNotice(kind, message, resume, now())
        current = notice
        events.tryEmit(ServerEvent.Notice(notice))
        return notice
    }

    fun clear() {
        current = null
        events.tryEmit(ServerEvent.Cleared)
    }

    fun shutdown() {
        announce(ServerNoticeRequest(kind = KIND_RESTART, resumeInSeconds = RESTART_RESUME_SECONDS))
        events.tryEmit(ServerEvent.Closed)
    }

    private companion object {
        const val KIND_RESTART = "restart"
        val KINDS = setOf(KIND_RESTART, "update", "maintenance")
        const val EVENT_BUFFER = 16
        const val MAX_MESSAGE_LENGTH = 280
        const val MAX_RESUME_SECONDS = 86_400
        const val RESTART_RESUME_SECONDS = 20
    }
}
