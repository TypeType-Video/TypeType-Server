package dev.typetype.server.services

import dev.typetype.server.downloader.OkHttpDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.YoutubeApiDecoder
import java.net.ProxySelector

object NewPipeInitializer {
    @Volatile private var initialized = false
    @Volatile private var decoderServiceUrl: String? = null

    fun init(
        tokenServiceUrl: String? = null,
        youtubeProxySelector: ProxySelector? = null,
    ): Unit {
        NewPipe.setYoutubePlayerClient(YOUTUBE_PLAYER_CLIENT)
        val normalizedUrl = tokenServiceUrl?.trim()?.takeIf { it.isNotBlank() }
        if (normalizedUrl != null && normalizedUrl != decoderServiceUrl) {
            YoutubeApiDecoder.setLocalDecoder(TypetypeTokenYoutubeJavaScriptDecoder(normalizedUrl))
            TypetypeYoutubeSessionPoTokenProvider.configureAuthenticatedProvider(
                TypetypeTokenYoutubeSessionPoTokenProvider(normalizedUrl),
            )
            decoderServiceUrl = normalizedUrl
        } else if (normalizedUrl == null) {
            TypetypeYoutubeSessionPoTokenProvider.configureAuthenticatedProvider(null)
        }
        if (!initialized) {
            NewPipe.init(OkHttpDownloader.instance(youtubeProxySelector))
            initialized = true
        }
        NewPipe.setYoutubeSessionPoTokenProvider(TypetypeYoutubeSessionPoTokenProvider)
    }

    private const val YOUTUBE_PLAYER_CLIENT = "mweb"
}
