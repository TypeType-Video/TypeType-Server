package dev.typetype.server.services

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.LiveChatMessage
import kotlinx.coroutines.flow.Flow

sealed interface YoutubeLiveChatEvent {
    data class Message(val message: LiveChatMessage) : YoutubeLiveChatEvent
    data class Error(val error: ErrorResponse) : YoutubeLiveChatEvent
    data object Heartbeat : YoutubeLiveChatEvent
}

interface YoutubeLiveChatSession : AutoCloseable {
    val events: Flow<YoutubeLiveChatEvent>
    override fun close()
}

sealed interface YoutubeLiveChatOpenResult {
    data class Opened(val session: YoutubeLiveChatSession) : YoutubeLiveChatOpenResult
    data class Unsupported(val message: String) : YoutubeLiveChatOpenResult
    data class Unavailable(val message: String) : YoutubeLiveChatOpenResult
    data object CapacityReached : YoutubeLiveChatOpenResult
}

interface YoutubeLiveChatService : AutoCloseable {
    suspend fun openSession(url: String): YoutubeLiveChatOpenResult
    override fun close()
}
