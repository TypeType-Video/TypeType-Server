package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamExtractor

class PipePipeLiveHlsStreamService(
    private val extractorForUrl: (String) -> StreamExtractor = ::createStreamExtractor,
) : StreamService {
    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
        if (!isYoutubeUrl(url)) return ExtractionResult.BadRequest("Invalid YouTube URL")

        return withContext(Dispatchers.IO) {
            try {
                val extractor = extractorForUrl(url)
                val response = withTimeout(LIVE_EXTRACTION_TIMEOUT_MS) {
                    runPipePipeCall {
                        extractor.fetchPage()
                        val streamType = extractor.streamType
                        val type = streamType.toApiStreamType()
                        if (!type.isLiveStreamType()) return@runPipePipeCall null

                        val hlsUrl = extractor.hlsUrl
                        if (hlsUrl.isBlank() || extractor.requiresMembership()) {
                            return@runPipePipeCall null
                        }
                        extractor.toLiveHlsResponse(type, hlsUrl)
                    }
                }
                response?.let { ExtractionResult.Success(it) }
                    ?: ExtractionResult.Failure(
                        "No active YouTube live stream is available",
                        "live_stream_unavailable",
                    )
            } catch (_: TimeoutCancellationException) {
                ExtractionResult.Failure("YouTube live extraction timed out", "live_stream_unavailable")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                StreamExtractionErrorMapper.map(error, sourceUrl = url)
            }
        }
    }

    private fun StreamExtractor.toLiveHlsResponse(streamType: String, hlsUrl: String) = StreamResponse(
        id = id,
        title = name.orEmpty(),
        uploaderName = optionalExtractorValue("") { uploaderName },
        uploaderUrl = optionalExtractorValue("") { uploaderUrl },
        uploaderAvatarUrl = optionalExtractorValue("") { uploaderAvatarUrl },
        thumbnailUrl = optionalExtractorValue("") { thumbnailUrl },
        description = optionalExtractorValue("") { description.content.orEmpty() },
        duration = optionalExtractorValue(0L) { length.coerceAtLeast(0L) },
        viewCount = optionalExtractorValue(-1L) { viewCount },
        likeCount = -1L,
        dislikeCount = -1L,
        uploadDate = "",
        uploaded = -1L,
        uploaderSubscriberCount = -1L,
        uploaderVerified = false,
        category = "",
        license = "",
        visibility = "public",
        tags = emptyList(),
        streamType = streamType,
        isLive = true,
        isPostLive = false,
        isLiveContent = true,
        hasLiveManifest = true,
        isShortFormContent = optionalExtractorValue(false) { isShortFormContent },
        requiresMembership = false,
        startPosition = optionalExtractorValue(0L) { timeStamp.coerceAtLeast(0L) },
        streamSegments = emptyList(),
        hlsUrl = hlsUrl,
        dashMpdUrl = "",
        videoStreams = emptyList(),
        audioStreams = emptyList(),
        originalAudioTrackId = null,
        preferredDefaultAudioTrackId = null,
        videoOnlyStreams = emptyList(),
        subtitles = emptyList(),
        previewFrames = emptyList(),
        sponsorBlockSegments = emptyList(),
        relatedStreams = emptyList(),
    )

    private companion object {
        const val LIVE_EXTRACTION_TIMEOUT_MS = 8_000L

        fun createStreamExtractor(url: String): StreamExtractor {
            val service = NewPipe.getServiceByUrl(url)
            return service.getStreamExtractor(service.streamLHFactory.fromUrl(url))
        }

        inline fun <T> optionalExtractorValue(default: T, value: () -> T): T =
            try {
                value()
            } catch (_: Exception) {
                default
            }
    }
}
