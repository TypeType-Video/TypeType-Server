package dev.typetype.server.services

internal object HlsWarmupPlan {
    data class ChildPlaylist(val handle: String, val startsPlayback: Boolean)

    data class MediaPlaylist(val headers: List<String>, val firstSegment: String?)

    fun fromMaster(master: String): List<ChildPlaylist> {
        val variants = mutableListOf<Pair<Long, String>>()
        val audio = mutableListOf<String>()
        var pendingBandwidth: Long? = null
        for (line in master.lineSequence().map(String::trim)) {
            when {
                line.startsWith("#EXT-X-STREAM-INF") ->
                    pendingBandwidth = BANDWIDTH.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: Long.MAX_VALUE
                line.startsWith("#EXT-X-MEDIA") && line.contains("TYPE=AUDIO") ->
                    uriAttribute(line)?.let(::handleOf)?.let(audio::add)
                line.isNotEmpty() && !line.startsWith("#") && pendingBandwidth != null -> {
                    handleOf(line)?.let { variants += pendingBandwidth to it }
                    pendingBandwidth = null
                }
            }
        }
        val lowest = variants.minByOrNull { it.first }?.second
        return variants.map { ChildPlaylist(it.second, it.second == lowest) } +
            audio.distinct().map { ChildPlaylist(it, true) }
    }

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

    private val BANDWIDTH = Regex("[:,]BANDWIDTH=(\\d+)")
    private val URI = Regex("URI=\"([^\"]+)\"")
}
