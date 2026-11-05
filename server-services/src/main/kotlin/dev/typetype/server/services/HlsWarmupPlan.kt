package dev.typetype.server.services

internal object HlsWarmupPlan {
    data class MediaPlaylist(val headers: List<String>, val firstSegment: String?)

    fun fromMediaPlaylist(playlist: String): MediaPlaylist {
        val headers = mutableListOf<String>()
        var firstSegment: String? = null
        for (line in playlist.lineSequence().map(String::trim)) {
            when {
                line.startsWith("#EXT-X-MAP") || line.startsWith("#EXT-X-KEY") ->
                    uriAttribute(line)?.let(::handleOf)?.let(headers::add)
                firstSegment == null && line.isNotEmpty() && !line.startsWith("#") -> firstSegment = handleOf(line)
            }
        }
        return MediaPlaylist(headers.distinct(), firstSegment)
    }

    private fun uriAttribute(line: String): String? = URI.find(line)?.groupValues?.get(1)

    private fun handleOf(uri: String): String? =
        uri.substringBefore('?').substringAfterLast('/')
            .takeIf(::isProviderMediaHandle)

    private val URI = Regex("URI=\"([^\"]+)\"")
}
