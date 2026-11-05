package dev.typetype.server.services

internal object HlsWarmupPlan {
    data class MediaPlaylist(val headers: List<String>, val segments: List<HlsSegmentChain.Segment>) {
        val firstSegment: String? get() = segments.firstOrNull()?.handle
    }

    fun fromMediaPlaylist(playlist: String): MediaPlaylist {
        val headers = mutableListOf<String>()
        val segments = mutableListOf<HlsSegmentChain.Segment>()
        var startMs = 0L
        var durationMs = 0L
        for (line in playlist.lineSequence().map(String::trim)) {
            when {
                line.startsWith("#EXT-X-MAP") || line.startsWith("#EXT-X-KEY") ->
                    uriAttribute(line)?.let(::handleOf)?.let(headers::add)
                line.startsWith("#EXTINF:") ->
                    durationMs = ((line.removePrefix("#EXTINF:").substringBefore(',').toDoubleOrNull() ?: 0.0) * 1000).toLong()
                line.isNotEmpty() && !line.startsWith("#") -> {
                    handleOf(line)?.let { segments += HlsSegmentChain.Segment(it, startMs) }
                    startMs += durationMs
                    durationMs = 0L
                }
            }
        }
        return MediaPlaylist(headers.distinct(), segments)
    }

    private fun uriAttribute(line: String): String? = URI.find(line)?.groupValues?.get(1)

    private fun handleOf(uri: String): String? =
        uri.substringBefore('?').substringAfterLast('/')
            .takeIf(::isProviderMediaHandle)

    private val URI = Regex("URI=\"([^\"]+)\"")
}
