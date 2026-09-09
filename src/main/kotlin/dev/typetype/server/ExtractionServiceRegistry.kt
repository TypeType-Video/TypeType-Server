package dev.typetype.server

import dev.typetype.server.cache.DragonflyService
import dev.typetype.server.services.BilibiliRelatedService
import dev.typetype.server.services.BilibiliTrendingService
import dev.typetype.server.services.AuthenticatedSabrInfoService
import dev.typetype.server.services.CachedChannelService
import dev.typetype.server.services.CachedCommentService
import dev.typetype.server.services.CachedManifestService
import dev.typetype.server.services.CachedNativeManifestService
import dev.typetype.server.services.CachedPodcastService
import dev.typetype.server.services.CachedPublicPlaylistService
import dev.typetype.server.services.CachedSearchService
import dev.typetype.server.services.CachedStreamService
import dev.typetype.server.services.CachedSuggestionService
import dev.typetype.server.services.CachedTrendingService
import dev.typetype.server.services.HlsManifestService
import dev.typetype.server.services.ManifestService
import dev.typetype.server.services.NativeManifestService
import dev.typetype.server.services.NicoNicoTrendingService
import dev.typetype.server.services.NicoVideoProxyService
import dev.typetype.server.services.OkHttpProxyService
import dev.typetype.server.services.PipePipeBulletCommentService
import dev.typetype.server.services.PipePipeChannelService
import dev.typetype.server.services.PipePipeCommentService
import dev.typetype.server.services.PipePipePodcastService
import dev.typetype.server.services.PipePipePublicPlaylistService
import dev.typetype.server.services.PipePipeSearchService
import dev.typetype.server.services.PipePipeStreamService
import dev.typetype.server.services.PipePipeSuggestionService
import dev.typetype.server.services.PipePipeTrendingService
import dev.typetype.server.services.SabrFallbackStreamService
import dev.typetype.server.services.SabrBootstrapStreamService
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.SignedHlsManifestTokenService
import dev.typetype.server.services.TypetypeTokenYoutubeSessionClient
import dev.typetype.server.services.TypetypeTokenSabrTokenClient
import dev.typetype.server.services.YouTubeSubtitleService
import dev.typetype.server.services.YouTubeSubtitleCache
import dev.typetype.server.services.YouTubeSubtitleDeliveryService
import dev.typetype.server.services.OkHttpYouTubeSubtitleContentFetcher
import dev.typetype.server.services.StreamYouTubeSubtitleResolver
import dev.typetype.server.services.TokenYouTubeSubtitleContentFetcher
import dev.typetype.server.services.YoutubePlayerClient
import dev.typetype.server.services.YoutubePlayerClientStreamService
import dev.typetype.server.services.YoutubeScopedChannelService
import dev.typetype.server.services.YoutubeScopedCommentService
import dev.typetype.server.services.YoutubeScopedPublicPlaylistService
import dev.typetype.server.services.YoutubeScopedSearchService
import dev.typetype.server.services.YoutubeScopedStreamService
import dev.typetype.server.services.YoutubeScopedSuggestionService
import dev.typetype.server.services.YoutubeScopedTrendingService
import dev.typetype.server.services.YoutubeSessionCrypto
import dev.typetype.server.services.YoutubeSessionHlsManifestService
import dev.typetype.server.services.YoutubeSessionService
import dev.typetype.server.services.YoutubeSessionStreamService
import dev.typetype.server.services.YoutubeSessionSabrStreamService
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.net.ProxySelector
import java.util.concurrent.TimeUnit

internal class ExtractionServiceRegistry(
    cache: DragonflyService,
    subtitleServiceUrl: String,
    youtubeSessionEncryptionKey: String?,
    hlsManifestUrlSigner: ((String) -> String)? = null,
    youtubeProxySelector: ProxySelector? = null,
) {
    private val youtubeSessionSecret = youtubeSessionEncryptionKey?.trim()
        ?.takeIf { it.length >= MIN_YOUTUBE_SESSION_SECRET_LENGTH }
    val httpClient = OkHttpClient.Builder()
        .apply { youtubeProxySelector?.let(::proxySelector) }
        .build()
    val proxyHttpClient: OkHttpClient = httpClient.newBuilder()
        .dispatcher(proxyDispatcher())
        .connectionPool(ConnectionPool(64, 5, TimeUnit.MINUTES))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    val sabrSessionStore = SabrSessionStore(subtitleServiceUrl, initCache = cache)
    val youtubeSubtitleService = YouTubeSubtitleService(httpClient, subtitleServiceUrl)
    private val bilibiliRelatedService = BilibiliRelatedService()
    private val directPipePipeStreamService = PipePipeStreamService(
        cache,
        youtubeSubtitleService,
        bilibiliRelatedService,
    )
    private val sabrPipePipeStreamService = PipePipeStreamService(
        cache,
        youtubeSubtitleService,
        bilibiliRelatedService,
        sabrSessionStore::rememberExtractedInfo,
    )
    private val publicStreamService = YoutubePlayerClientStreamService(
        directPipePipeStreamService,
        YoutubePlayerClient.VISIONOS,
    )
    private val authenticatedStreamService = YoutubePlayerClientStreamService(
        directPipePipeStreamService,
        YoutubePlayerClient.MWEB,
    )
    private val sabrPublicStreamService = YoutubePlayerClientStreamService(
        sabrPipePipeStreamService,
        YoutubePlayerClient.MWEB,
    )
    val youtubeSessionService = YoutubeSessionService(youtubeSessionSecret?.let(YoutubeSessionCrypto::fromSecret))
    val authenticatedSabrInfoService = AuthenticatedSabrInfoService(
        youtubeSessionService,
        TypetypeTokenSabrTokenClient(subtitleServiceUrl, httpClient),
    )
    private val hlsTokenService = youtubeSessionSecret?.let(::SignedHlsManifestTokenService)
    private val tokenYoutubeSessionClient = TypetypeTokenYoutubeSessionClient(subtitleServiceUrl, httpClient)
    val youtubeSessionStreamService = hlsTokenService?.let {
        YoutubeSessionStreamService(authenticatedStreamService, youtubeSessionService, cache, it)
    }
    val youtubeSessionSabrStreamService = youtubeSessionStreamService?.let {
        YoutubeSessionSabrStreamService(it, authenticatedSabrInfoService)
    }
    val youtubeSabrStreamService = CachedStreamService(
        YoutubeScopedStreamService(
            SabrFallbackStreamService(sabrPublicStreamService, sabrSessionStore, tokenYoutubeSessionClient),
        ),
        cache,
        "stream-youtube-sabr:v1",
    )
    val youtubeSubtitleDeliveryService = YouTubeSubtitleDeliveryService(
        StreamYouTubeSubtitleResolver(youtubeSabrStreamService, youtubeSubtitleService::fetchSubtitleInventory),
        TokenYouTubeSubtitleContentFetcher(
            httpClient,
            subtitleServiceUrl,
            OkHttpYouTubeSubtitleContentFetcher(httpClient),
        ),
        YouTubeSubtitleCache(cache),
    )
    val youtubeSabrBootstrapStreamService = YoutubeScopedStreamService(
        SabrBootstrapStreamService(sabrSessionStore, tokenYoutubeSessionClient),
    )
    val nicoNicoStreamService = CachedStreamService(directPipePipeStreamService, cache, "stream-niconico:v1")
    val bilibiliStreamService = CachedStreamService(directPipePipeStreamService, cache, "stream-bilibili:v1")
    val streamService = CachedStreamService(publicStreamService, cache, "stream-direct:v1")
    val searchService = CachedSearchService(YoutubeScopedSearchService(PipePipeSearchService()), cache)
    val trendingService = CachedTrendingService(
        YoutubeScopedTrendingService(PipePipeTrendingService(BilibiliTrendingService(), NicoNicoTrendingService(httpClient))),
        cache,
    )
    val commentService = CachedCommentService(YoutubeScopedCommentService(PipePipeCommentService()), cache)
    val bulletCommentService = PipePipeBulletCommentService()
    val channelService = CachedChannelService(YoutubeScopedChannelService(PipePipeChannelService()), cache)
    val podcastService = CachedPodcastService(PipePipePodcastService(), cache)
    val publicPlaylistService = CachedPublicPlaylistService(
        YoutubeScopedPublicPlaylistService(PipePipePublicPlaylistService()),
        cache,
    )
    val proxyService = OkHttpProxyService(proxyHttpClient)
    val nicoVideoProxyService = NicoVideoProxyService()
    val manifestService = CachedManifestService(ManifestService(streamService), cache)
    val nativeManifestService = CachedNativeManifestService(NativeManifestService(), cache)
    val hlsManifestService = HlsManifestService(
        streamService,
        proxyHttpClient,
        cache,
        hlsManifestUrlSigner,
        tokenYoutubeSessionClient::fetchHlsManifestUrl,
    )
    val youtubeSessionHlsManifestService = hlsTokenService?.let { tokenService ->
        youtubeSessionStreamService?.let {
            YoutubeSessionHlsManifestService(youtubeSessionService, it, hlsManifestService, tokenService)
        }
    }
    val suggestionService = CachedSuggestionService(YoutubeScopedSuggestionService(PipePipeSuggestionService()), cache)

    private companion object {
        const val MIN_YOUTUBE_SESSION_SECRET_LENGTH = 32

        fun proxyDispatcher(): Dispatcher = Dispatcher().apply {
            maxRequests = 256
            maxRequestsPerHost = 64
        }
    }
}
