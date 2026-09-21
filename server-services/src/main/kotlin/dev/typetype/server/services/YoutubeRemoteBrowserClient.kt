package dev.typetype.server.services

import io.ktor.server.websocket.DefaultWebSocketServerSession

interface YoutubeRemoteBrowserClient {
    suspend fun start(
        request: YoutubeRemoteBrowserTokenStartRequest,
        internalToken: String,
    ): YoutubeRemoteBrowserTokenStartResponse?

    suspend fun cancel(tokenSessionId: String, internalToken: String): Boolean

    suspend fun bridge(
        serverSession: DefaultWebSocketServerSession,
        tokenSessionId: String,
        internalToken: String,
        config: YoutubeRemoteBrowserConfig,
    )
}
