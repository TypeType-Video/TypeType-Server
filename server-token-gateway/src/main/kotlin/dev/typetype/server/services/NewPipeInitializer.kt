package dev.typetype.server.services

import dev.typetype.server.downloader.BilibiliCookieContext
import dev.typetype.server.downloader.OkHttpDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.YoutubeApiDecoder
import org.slf4j.LoggerFactory
import java.net.ProxySelector

object NewPipeInitializer {
    @Volatile private var initialized = false
    @Volatile private var decoderServiceUrl: String? = null
    private val log = LoggerFactory.getLogger(NewPipeInitializer::class.java)

    @Synchronized
    fun init(
        tokenServiceUrl: String? = null,
        youtubeProxySelector: ProxySelector? = null,
        bilibiliCookie: BilibiliCookieConfig = BilibiliCookieConfig.fromEnvironment(),
    ): Unit {
        configureBilibili(bilibiliCookie)
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

    private fun configureBilibili(config: BilibiliCookieConfig) {
        ServiceList.BiliBili.setTokens(config.cookieHeader.orEmpty())
        ServiceList.BiliBili.setCookieFunctions(
            if (config.isConfigured) BILIBILI_COOKIE_FUNCTIONS else emptySet(),
        )
        BilibiliCookieContext.set(config.cookieHeader)
        if (config.wasProvided && !config.isConfigured) {
            log.warn("Ignoring invalid BILIBILI_COOKIE; expected a cookie header containing an account cookie")
        } else if (config.isConfigured) {
            log.info("BiliBili account cookie configured for instance extraction")
        }
    }

    private const val YOUTUBE_PLAYER_CLIENT = "mweb"
    private val BILIBILI_COOKIE_FUNCTIONS = setOf("high_res", "ai_subtitle")
}
