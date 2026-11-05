package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.localization.Localization
import java.io.ByteArrayOutputStream
import java.util.Base64

class CommentVoiceAudio(val bytes: ByteArray, val mimeType: String)

class CommentVoiceAudioService(
    private val store: SabrSessionStore,
    private val cache: CacheService,
) {
    private val localization = Localization("en", "US")
    private val inFlight = KeyedSingleFlight<String, CommentVoiceAudio?>()

    suspend fun audio(videoId: String): CommentVoiceAudio? = inFlight.run(videoId) { load(videoId) }

    private suspend fun load(videoId: String): CommentVoiceAudio? {
        runCatching { cache.get(cacheKey(videoId)) }.getOrNull()
            ?.let { cached -> runCatching { Base64.getDecoder().decode(cached) }.getOrNull() }
            ?.let { return CommentVoiceAudio(it, MIME_TYPE) }
        val prepared = store.fetchAudioOnlyMediaInfo(videoId)?.takeIf { it.isAudioOnlyMedia() } ?: return null
        val audio = prepared.info.formats.filter { it.isAudio && it.mimeType?.startsWith(MIME_TYPE) == true }
            .maxByOrNull { it.bitrate } ?: return null
        if (audio.approxDurationMs !in 1..MAX_DURATION_MS) return null
        val holder = store.getOrCreate(
            videoId,
            OWNER,
            prepared.info,
            audio,
            audio,
            prepared.initialToken,
            startPump = false,
            purpose = SabrSessionPurpose.DOWNLOAD,
            audioOnly = true,
        )
        val bytes = try {
            holder.setActiveTracks(videoActive = false, audioActive = true)
            download(holder, audio)
        } finally {
            store.release(holder)
        } ?: return null
        runCatching { cache.set(cacheKey(videoId), Base64.getEncoder().encodeToString(bytes), CACHE_TTL_SECONDS) }
        return CommentVoiceAudio(bytes, MIME_TYPE)
    }

    private suspend fun download(holder: SabrSessionHolder, audio: YoutubeSabrFormat): ByteArray? {
        val output = ByteArrayOutputStream()
        output.write(SabrDownloadInitialization.fetch(store, holder, audio) ?: return null)
        holder.session.discardCachedSegment(SabrSegmentRequest.initialization(audio))
        var sequence = 1
        var emptyPumps = 0
        while (output.size() <= MAX_BYTES) {
            val request = SabrSegmentRequest.media(audio, sequence)
            val segment = holder.session.getCachedSegment(request)
            if (segment != null) {
                segment.openStream().use { it.copyTo(output) }
                holder.session.discardCachedSegment(request)
                sequence++
                emptyPumps = 0
                continue
            }
            if (sequence > 1 && (holder.session.streamState.isComplete(audio) || holder.session.isBeyondEnd(request))) {
                return output.toByteArray()
            }
            val playerTimeMs = holder.session.streamState.getSegmentStartMs(audio, sequence).coerceAtLeast(0L)
            val pumped = withContext(Dispatchers.IO) {
                withTimeoutOrNull(PUMP_TIMEOUT_MS) {
                    runInterruptible {
                        holder.withPlayerContext {
                            prepareForForwardJump(request, playerTimeMs)
                            pumpOnceStreamingUntilCached(localization, request)
                        }
                    }
                }
            } ?: return null
            if (pumped == 0 && ++emptyPumps > MAX_EMPTY_PUMPS) return null
        }
        return null
    }

    private fun cacheKey(videoId: String): String = "comment-voice:v1:$videoId"

    private companion object {
        const val OWNER = "comment-voice"
        const val MIME_TYPE = "audio/mp4"
        const val MAX_DURATION_MS = 120_000L
        const val MAX_BYTES = 4 * 1024 * 1024
        const val MAX_EMPTY_PUMPS = 6
        const val PUMP_TIMEOUT_MS = 15_000L
        const val CACHE_TTL_SECONDS = 86_400L
    }
}
