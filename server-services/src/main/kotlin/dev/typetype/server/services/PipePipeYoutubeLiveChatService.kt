package dev.typetype.server.services

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.LiveChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfo
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsExtractor
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class PipePipeYoutubeLiveChatService : YoutubeLiveChatService {
    private val capacity = Semaphore(MAX_SESSIONS)
    private val sessions = ConcurrentHashMap.newKeySet<LiveChatSession>()
    private val closed = AtomicBoolean(false)

    override suspend fun openSession(url: String): YoutubeLiveChatOpenResult {
        if (closed.get()) return YoutubeLiveChatOpenResult.Unavailable("Live chat is unavailable")
        if (!capacity.tryAcquire()) return YoutubeLiveChatOpenResult.CapacityReached
        var session: LiveChatSession? = null
        var pendingExtractor: BulletCommentsExtractor? = null
        try {
            val registeredService = try {
                NewPipe.getServiceByUrl(url)
            } catch (_: Exception) {
                return YoutubeLiveChatOpenResult.Unsupported("Unsupported video URL")
            }
            if (registeredService.serviceId != YOUTUBE_SERVICE_ID) {
                return YoutubeLiveChatOpenResult.Unsupported("Live chat is only available for YouTube")
            }

            val service = YoutubeService(YOUTUBE_SERVICE_ID)
            val streamExtractor = service.getStreamExtractor(url)
            withTimeout(EXTRACTION_TIMEOUT_MS) { runPipePipeCall { streamExtractor.fetchPage() } }
            val streamInfo = withTimeout(EXTRACTION_TIMEOUT_MS) {
                runPipePipeCall { StreamInfo.getInfo(streamExtractor) }
            }
            if (streamInfo.streamType !in LIVE_STREAM_TYPES) {
                return YoutubeLiveChatOpenResult.Unsupported("Live chat requires a live YouTube video")
            }

            val chatExtractor = service.getBulletCommentsExtractor(url)
            pendingExtractor = chatExtractor
            withTimeout(EXTRACTION_TIMEOUT_MS) {
                runPipePipeCall { BulletCommentsInfo.getInfo(chatExtractor) }
            }
            if (chatExtractor.isDisabled) {
                return YoutubeLiveChatOpenResult.Unsupported("Live chat is unavailable for this video")
            }

            session = LiveChatSession(chatExtractor, streamInfo.startAt)
            pendingExtractor = null
            sessions.add(session)
            if (closed.get()) {
                session.close()
                return YoutubeLiveChatOpenResult.Unavailable("Live chat is unavailable")
            }
            return YoutubeLiveChatOpenResult.Opened(session)
        } catch (_: TimeoutCancellationException) {
            return YoutubeLiveChatOpenResult.Unavailable("YouTube did not respond in time")
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return YoutubeLiveChatOpenResult.Unavailable("Could not start YouTube live chat")
        } finally {
            if (session == null) {
                pendingExtractor?.disconnect()
                capacity.release()
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        sessions.toList().forEach { it.close() }
    }

    private inner class LiveChatSession(
        private val extractor: BulletCommentsExtractor,
        streamStartAtMs: Long,
    ) : YoutubeLiveChatSession {
        private val isClosed = AtomicBoolean(false)
        private val startAtMs = streamStartAtMs.takeIf { it > 0L }
            ?.coerceAtMost(System.currentTimeMillis()) ?: System.currentTimeMillis()
        private var sequence = 0L
        override val events: Flow<YoutubeLiveChatEvent> = flow {
            var lastHeartbeatAt = System.currentTimeMillis()
            while (!isClosed.get() && currentCoroutineContext().isActive) {
                val now = System.currentTimeMillis()
                extractor.setCurrentPlayPosition((now - startAtMs).coerceAtLeast(1L))
                runPipePipeCall { extractor.liveMessages }.forEach { item ->
                    val text = item.commentText?.trim()?.takeIf(String::isNotEmpty) ?: return@forEach
                    sequence += 1
                    emit(YoutubeLiveChatEvent.Message(LiveChatMessage("chat-$sequence", text, now)))
                }
                if (now - lastHeartbeatAt >= HEARTBEAT_INTERVAL_MS) {
                    emit(YoutubeLiveChatEvent.Heartbeat)
                    lastHeartbeatAt = now
                }
                delay(POLL_INTERVAL_MS)
            }
        }.onCompletion { close() }

        override fun close() {
            if (!isClosed.compareAndSet(false, true)) return
            try {
                extractor.disconnect()
            } finally {
                sessions.remove(this)
                capacity.release()
            }
        }
    }

    private companion object {
        const val MAX_SESSIONS = 12
        const val EXTRACTION_TIMEOUT_MS = 25_000L
        const val POLL_INTERVAL_MS = 1_000L
        const val HEARTBEAT_INTERVAL_MS = 15_000L
        val LIVE_STREAM_TYPES = setOf(StreamType.LIVE_STREAM, StreamType.AUDIO_LIVE_STREAM)
    }
}
