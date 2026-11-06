package dev.typetype.server.services

import dev.typetype.server.models.AudioStreamItem
import dev.typetype.server.models.VideoStreamItem

/** The tracks the web player opens first, which the warmup fetches ahead of the player. */
internal object ProviderMediaStartTracks {
    fun urls(
        provider: ProviderMediaType,
        video: List<VideoStreamItem>,
        audio: List<AudioStreamItem>,
    ): Set<String> = when (provider) {
        // hls.js picks the NicoNico start level from its own estimate, and the first segments are small.
        ProviderMediaType.NICONICO -> (video.map { it.url } + audio.map { it.url }).toSet()
        ProviderMediaType.BILIBILI -> {
            val group = video.groupBy { webCodecRank(it.codec) }.minByOrNull { it.key }?.value.orEmpty()
            val start = group.filter { (it.bitrate ?: 0) in 1..BILIBILI_START_BITS_PER_SECOND }.maxByOrNull { it.bitrate ?: 0 }
                ?: group.minByOrNull { it.bitrate ?: Int.MAX_VALUE }
            setOfNotNull(start?.url, audio.maxByOrNull { it.bitrate ?: 0 }?.url)
        }
    }

    // The web player starts on the first codec family it supports in this order.
    private fun webCodecRank(codec: String?): Int = when {
        codec == null -> 3
        codec.startsWith("avc1") -> 0
        codec.startsWith("av01") -> 1
        codec.startsWith("hev1") || codec.startsWith("hvc1") -> 2
        else -> 3
    }

    const val BILIBILI_START_BITS_PER_SECOND = 1_000_000
}
