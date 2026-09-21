package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

class NativeManifestService {

    suspend fun nativeManifest(videoUrl: String): ExtractionResult<String> =
        if (isYoutubeUrl(videoUrl)) {
            YoutubeSessionTokenScope.withoutCredentials { nativeManifestWithoutCredentials(videoUrl) }
        } else {
            nativeManifestWithoutCredentials(videoUrl)
        }

    private suspend fun nativeManifestWithoutCredentials(videoUrl: String): ExtractionResult<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                withExtractionRetry {
                    withTimeout(30_000L) {
                        val service = NewPipe.getServiceByUrl(videoUrl)
                        val linkHandler = service.streamLHFactory.fromUrl(videoUrl)
                        StreamInfo.getInfo(service, linkHandler.url)
                    }
                }
            }.fold(
                onSuccess = { buildManifest(it) },
                onFailure = { StreamExtractionErrorMapper.map(it, sourceUrl = videoUrl) }
            )
        }

    private fun buildManifest(info: StreamInfo): ExtractionResult<String> {
        val videos = compatibleVideoStreams(info.videoOnlyStreams)
        val audios = compatibleAudioStreams(info.audioStreams)
        val preferredAudioTrackId = resolvePreferredAudioTrackId(audios)
        if (videos.isEmpty() && audios.isEmpty())
            return ExtractionResult.Failure("No compatible streams found", "no_playable_streams")
        return runCatching {
            ExtractionResult.Success(
                NativeManifestBuilder.build(videos, audios, info.duration, preferredAudioTrackId)
            )
        }.getOrElse {
            ExtractionResult.Failure(it.message ?: "Manifest build failed")
        }
    }

    private fun compatibleVideoStreams(streams: List<VideoStream>): List<VideoStream> =
        streams.filter { s ->
            val codec = s.getCodec()
            !codec.isNullOrBlank() &&
                !codec.startsWith("av01") &&
                !codec.startsWith("vp9") &&
                !codec.startsWith("vp09") &&
                (s.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP || s.deliveryMethod == DeliveryMethod.DASH) &&
                s.getContent()?.isNotBlank() == true &&
                s.getItagItem() != null
        }.sortedWith(compareBy({ codecPriority(s = it) }, { -(it.getBitrate()) }))

    private fun compatibleAudioStreams(streams: List<AudioStream>): List<AudioStream> =
        streams.filter { s ->
            val codec = s.getCodec()
            !codec.isNullOrBlank() &&
                !codec.startsWith("opus") &&
                !codec.startsWith("vorbis") &&
                s.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                s.getContent()?.isNotBlank() == true &&
                s.getItagItem() != null
        }.sortedByDescending { it.averageBitrate }

    private fun codecPriority(s: VideoStream): Int {
        val codec = s.getCodec() ?: return 2
        return when {
            codec.startsWith("avc1") -> 0
            codec.startsWith("vp9") || codec.startsWith("vp09") -> 1
            else -> 2
        }
    }

    private fun resolvePreferredAudioTrackId(audios: List<AudioStream>): String? {
        val original = audios.firstNotNullOfOrNull { stream ->
            val name = stream.getAudioTrackName()?.lowercase() ?: return@firstNotNullOfOrNull null
            stream.getAudioTrackId()?.takeIf { "original" in name || "default" in name || "yokuqala" in name }
        }
        if (original != null) return original
        val english = audios.firstNotNullOfOrNull { stream ->
            stream.getAudioTrackId()?.takeIf {
                stream.getAudioLocale() == "en" || it.substringBefore('.').substringBefore('-') == "en"
            }
        }
        if (english != null) return english
        return audios.firstNotNullOfOrNull { it.getAudioTrackId() }
    }
}
