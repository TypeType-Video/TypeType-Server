package dev.typetype.server

import dev.typetype.server.services.TypetypeTokenHttpClient
import dev.typetype.server.services.YoutubeTvOauthPlayerClient
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.net.ProxySelector
import java.util.concurrent.TimeUnit

internal class YoutubeEgressClients(youtubeProxySelector: ProxySelector?) {
    val base: OkHttpClient = OkHttpClient.Builder()
        .apply { youtubeProxySelector?.let(::proxySelector) }
        .build()
    val subtitle: OkHttpClient = base.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
    val proxy: OkHttpClient = base.newBuilder()
        .dispatcher(proxyDispatcher())
        .connectionPool(ConnectionPool(64, 5, TimeUnit.MINUTES))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    val token: OkHttpClient = TypetypeTokenHttpClient.bounded(base)
    val youtubeApi: OkHttpClient = base.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    fun tvPlayerClient(): YoutubeTvOauthPlayerClient = YoutubeTvOauthPlayerClient(youtubeApi)

    private fun proxyDispatcher(): Dispatcher = Dispatcher().apply {
        maxRequests = 256
        maxRequestsPerHost = 64
    }
}
