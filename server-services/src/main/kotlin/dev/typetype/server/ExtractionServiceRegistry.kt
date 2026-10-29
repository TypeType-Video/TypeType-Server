package dev.typetype.server

import dev.typetype.server.cache.DragonflyService
import dev.typetype.server.services.BilibiliAggregatedSearchService
import dev.typetype.server.services.BilibiliRelatedService
import dev.typetype.server.services.BilibiliTrendingService
import dev.typetype.server.services.AuthenticatedSabrInfoService
import dev.typetype.server.services.CachedChannelService
import dev.typetype.server.services.CachedChannelVideosSource
import dev.typetype.server.services.CachedImageProxyService
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
import dev.typetype.server.services.PipePipeLiveHlsStreamService
import dev.typetype.server.services.PipePipeSuggestionService
import dev.typetype.server.services.PipePipeTrendingService
import dev.typetype.server.services.ProviderMediaHandleService
import dev.typetype.server.services.SabrFallbackStreamService
import dev.typetype.server.services.SabrBootstrapCache
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
import dev.typetype.server.services.YoutubeLiveHlsStreamService
import dev.typetype.server.services.YoutubeDirectLiveHlsStreamService
import dev.typetype.server.services.YoutubeScopedChannelService
import dev.typetype.server.services.YoutubeScopedChannelVideosSource
import dev.typetype.server.services.YoutubeScopedCommentService
import dev.typetype.server.services.YoutubeScopedPublicPlaylistService
import dev.typetype.server.services.YoutubeScopedSearchService
import dev.typetype.server.services.YoutubeScopedStreamService
import dev.typetype.server.services.YoutubeStreamGuards
import dev.typetype.server.services.YoutubeScopedSuggestionService
import dev.typetype.server.services.YoutubeScopedTrendingService
import dev.typetype.server.services.YoutubeSessionCrypto
import dev.typetype.server.services.YoutubeSessionHlsManifestService
import dev.typetype.server.services.YoutubeSessionRefreshService
import dev.typetype.server.services.YoutubeSessionService
import dev.typetype.server.services.YoutubeSessionStore
import dev.typetype.server.services.YoutubeSessionStreamService
import dev.typetype.server.services.YoutubeSessionSabrStreamService
import okhttp3.OkHttpClient
import java.net.ProxySelector

internal class ExtractionServiceRegistry(
    cache: DragonflyService,
    subtitleServiceUrl: String,
    youtubeSessionEncryptionKey: String?,
    hlsManifestUrlSigner: ((String) -> String)? = null,
    youtubeProxySelector: ProxySelector? = null,
) {
    private val youtubeSessionSecret = youtubeSessionEncryptionKey?.trim()
        ?.takeIf { it.length >= MIN_YOUTUBE_SESSION_SECRET_LENGTH }
    private val egress = YoutubeEgressClients(youtubeProxySelector)
    private val httpClient = egress.base
    private val subtitleHttpClient = egress.subtitle
    private val proxyHttpClient: OkHttpClient = egress.proxy
    private val tokenYoutubeSessionClient = TypetypeTokenYoutubeSessionClient(subtitleServiceUrl, egress.token)
    private val sabrTokenClient = TypetypeTokenSabrTokenClient(subtitleServiceUrl, egress.token)
    val sabrSessionStore = SabrSessionStore(
        subtitleServiceUrl,
        tokenClient = sabrTokenClient,
        sessionClient = tokenYoutubeSessionClient,
        initCache = cache,
    )
    val youtubeSubtitleService = YouTubeSubtitleService(subtitleHttpClient, subtitleServiceUrl)
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
        sabrSessionStore::rememberExtractedInfoAsync,
        fetchSupplementalSubtitles = false,
    )
    private val publicStreamService = YoutubePlayerClientStreamService(
        directPipePipeStreamService,
        YoutubePlayerClient.VISIONOS,
    )
    private val liveHlsStreamService = YoutubePlayerClientStreamService(
        PipePipeLiveHlsStreamService(),
        YoutubePlayerClient.TV_DOWNGRADED,
    )
    private val authenticatedStreamService = YoutubeLiveHlsStreamService(
        YoutubePlayerClientStreamService(directPipePipeStreamService, YoutubePlayerClient.MWEB),
        liveHlsStreamService,
    )
    private val sabrPublicStreamService = YoutubePlayerClientStreamService(
        sabrPipePipeStreamService,
        YoutubePlayerClient.MWEB,
    )
    private val youtubeSessionCrypto = youtubeSessionSecret?.let(YoutubeSessionCrypto::fromSecret)
    private val youtubeSessionStore = YoutubeSessionStore()
    val youtubeSessionService =
        YoutubeSessionService(youtubeSessionCrypto, store = youtubeSessionStore, bindingClient = tokenYoutubeSessionClient)
    val youtubeSessionRefreshService =
        YoutubeSessionRefreshService(youtubeSessionCrypto, youtubeSessionStore)
    val authenticatedSabrInfoService =
        AuthenticatedSabrInfoService(
            youtubeSessionService = youtubeSessionService,
            tokenClient = sabrTokenClient,
            tvClient = egress.tvPlayerClient(),
            bindingClient = tokenYoutubeSessionClient,
            sessionEpoch = sabrTokenClient.sessionEpoch,
        )
    private val hlsTokenService = youtubeSessionSecret?.let(::SignedHlsManifestTokenService)
    private val youtubeSabrFallbackStreamService = SabrFallbackStreamService(
        sabrPublicStreamService,
        sabrSessionStore,
        tokenYoutubeSessionClient,
    )
    val youtubeSessionStreamService = hlsTokenService?.let {
        YoutubeSessionStreamService(authenticatedStreamService, youtubeSessionService, cache, it)
    }
    val youtubeSessionSabrStreamService = youtubeSessionStreamService?.let {
        YoutubeSessionSabrStreamService(it, authenticatedSabrInfoService, cache)
    }
    private val youtubeStreamGuards = YoutubeStreamGuards(sabrTokenClient)
    val streamRequestWatch get() = youtubeStreamGuards.watch
    val youtubeSabrStreamService = youtubeStreamGuards.cached(
        YoutubeScopedStreamService(YoutubeLiveHlsStreamService(youtubeSabrFallbackStreamService, liveHlsStreamService)),
        cache,
        "stream-youtube-sabr:v1",
    )
    val youtubeLiveHlsStreamService = youtubeStreamGuards.cached(
        YoutubeScopedStreamService(
            YoutubeDirectLiveHlsStreamService(
                liveHlsStreamService,
                youtubeSabrFallbackStreamService,
            ),
        ),
        cache,
        "stream-youtube-live-hls:v1",
    )
    val youtubeSubtitleDeliveryService = YouTubeSubtitleDeliveryService(
        StreamYouTubeSubtitleResolver(youtubeSabrStreamService, youtubeSubtitleService::fetchSubtitleInventory),
        TokenYouTubeSubtitleContentFetcher(
            subtitleHttpClient,
            subtitleServiceUrl,
            OkHttpYouTubeSubtitleContentFetcher(subtitleHttpClient),
        ),
        YouTubeSubtitleCache(cache),
    )
    val youtubeSabrBootstrapStreamService = YoutubeScopedStreamService(
        SabrBootstrapStreamService(
            sabrSessionStore,
            tokenYoutubeSessionClient,
            liveHlsStreamService,
            SabrBootstrapCache(sabrSessionStore.sessionEpoch::current, sabrSessionStore::hasPreparedInfo),
        ),
    )
    val nicoNicoStreamService = CachedStreamService(directPipePipeStreamService, cache, "stream-niconico:v1")
    val bilibiliStreamService = CachedStreamService(directPipePipeStreamService, cache, "stream-bilibili:v4")
    val streamService = youtubeStreamGuards.cached(publicStreamService, cache, "stream-direct:v1")
    val searchService = CachedSearchService(
        YoutubeScopedSearchService(
            BilibiliAggregatedSearchService(PipePipeSearchService()),
        ),
        cache,
    )
    val trendingService = CachedTrendingService(
        YoutubeScopedTrendingService(PipePipeTrendingService(BilibiliTrendingService(), NicoNicoTrendingService(httpClient))),
        cache,
    )
    val commentService = CachedCommentService(YoutubeScopedCommentService(PipePipeCommentService()), cache)
    val bulletCommentService = PipePipeBulletCommentService()
    private val pipePipeChannelService = PipePipeChannelService()
    val channelService = CachedChannelService(YoutubeScopedChannelService(pipePipeChannelService), cache)
    val channelVideosSource = CachedChannelVideosSource(
        channelService,
        YoutubeScopedChannelVideosSource(pipePipeChannelService),
        cache,
    )
    val podcastService = CachedPodcastService(PipePipePodcastService(), cache)
    val publicPlaylistService = CachedPublicPlaylistService(
        YoutubeScopedPublicPlaylistService(PipePipePublicPlaylistService()),
        cache,
    )
    val providerMediaHandleService = ProviderMediaHandleService(cache)
    val proxyService = CachedImageProxyService(OkHttpProxyService(proxyHttpClient, providerMediaHandleService), cache)
    val nicoVideoProxyService = NicoVideoProxyService(mediaHandleService = providerMediaHandleService)
    val manifestService = CachedManifestService(ManifestService(streamService, providerMediaHandleService), cache)
    val nativeManifestService = CachedNativeManifestService(NativeManifestService(), cache)
    val hlsManifestService = HlsManifestService(
        streamService,
        proxyHttpClient,
        cache,
        hlsManifestUrlSigner,
        tokenYoutubeSessionClient::fetchHlsManifestUrl,
        providerMediaHandleService,
    )
    val youtubeSessionHlsManifestService = hlsTokenService?.let { tokenService ->
        youtubeSessionStreamService?.let {
            YoutubeSessionHlsManifestService(youtubeSessionService, it, hlsManifestService, tokenService)
        }
    }
    val suggestionService = CachedSuggestionService(YoutubeScopedSuggestionService(PipePipeSuggestionService()), cache)

    private companion object {
        const val MIN_YOUTUBE_SESSION_SECRET_LENGTH = 32
    }
}
