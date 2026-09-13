package dev.typetype.server.services

import io.ktor.websocket.Frame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.atomic.AtomicInteger

class YoutubeRemoteBrowserBridgeListener(
    private val tokenSessionId: String,
    private val outbound: Channel<Frame>,
    private val done: CompletableDeferred<Unit>,
    private val config: YoutubeRemoteBrowserConfig,
) : WebSocketListener() {
    val texts = AtomicInteger()
    val frames = AtomicInteger()

    override fun onOpen(webSocket: WebSocket, response: Response) {
        YoutubeRemoteBrowserAudit.bridgeOpened(tokenSessionId)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val accepted = YoutubeRemoteBrowserMessageGuard.tokenText(text)
        if (accepted == null) {
            YoutubeRemoteBrowserAudit.droppedTokenMessage(tokenSessionId, text)
            return
        }
        texts.incrementAndGet()
        outbound.trySend(Frame.Text(accepted))
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        if (bytes.size > config.maxFrameBytes) return
        frames.incrementAndGet()
        outbound.trySend(Frame.Binary(true, bytes.toByteArray()))
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        YoutubeRemoteBrowserAudit.bridgeClosed(tokenSessionId, code, reason)
        done.complete(Unit)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        YoutubeRemoteBrowserAudit.bridgeFailed(tokenSessionId, t)
        done.complete(Unit)
    }
}
