package dev.typetype.server

import dev.typetype.server.services.YoutubeRemoteBrowserClient
import dev.typetype.server.services.YoutubeRemoteBrowserConfig
import dev.typetype.server.services.YoutubeRemoteBrowserTokenStartRequest
import dev.typetype.server.services.YoutubeRemoteBrowserTokenStartResponse
import io.ktor.server.websocket.DefaultWebSocketServerSession

class FakeRemoteBrowserClient : YoutubeRemoteBrowserClient {
    override suspend fun start(
        request: YoutubeRemoteBrowserTokenStartRequest,
        internalToken: String,
    ): YoutubeRemoteBrowserTokenStartResponse =
        YoutubeRemoteBrowserTokenStartResponse("token-session", System.currentTimeMillis() + 480_000)

    override suspend fun cancel(tokenSessionId: String, internalToken: String): Boolean = true

    override suspend fun bridge(
        serverSession: DefaultWebSocketServerSession,
        tokenSessionId: String,
        internalToken: String,
        config: YoutubeRemoteBrowserConfig,
    ): Unit = Unit
}
