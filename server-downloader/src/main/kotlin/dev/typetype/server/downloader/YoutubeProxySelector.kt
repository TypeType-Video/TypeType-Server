package dev.typetype.server.downloader

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

class YoutubeProxySelector private constructor(
    private val proxy: Proxy,
) : ProxySelector() {
    override fun select(uri: URI): List<Proxy> =
        if (isYoutubeHost(uri.host)) listOf(proxy) else DIRECT

    override fun connectFailed(uri: URI, socketAddress: SocketAddress, exception: IOException) = Unit

    companion object {
        fun fromUrl(proxyUrl: String?): ProxySelector? {
            val value = proxyUrl?.trim()?.takeIf(String::isNotEmpty) ?: return null
            val uri = runCatching { URI(value) }
                .getOrElse { throw IllegalArgumentException("Invalid YouTube outbound proxy URL", it) }
            require(uri.scheme.equals("http", ignoreCase = true)) {
                "YOUTUBE_OUTBOUND_PROXY_URL must use the http scheme"
            }
            require(!uri.host.isNullOrBlank()) {
                "YOUTUBE_OUTBOUND_PROXY_URL must include a host"
            }
            require(uri.userInfo == null && uri.path.isNullOrEmpty() && uri.query == null && uri.fragment == null) {
                "YOUTUBE_OUTBOUND_PROXY_URL must contain only a scheme, host, and port"
            }
            val port = uri.port.takeIf { it >= 0 } ?: DEFAULT_HTTP_PORT
            val address = InetSocketAddress.createUnresolved(uri.host, port)
            return YoutubeProxySelector(Proxy(Proxy.Type.HTTP, address))
        }

        private fun isYoutubeHost(host: String?): Boolean {
            val normalizedHost = host?.lowercase() ?: return false
            return YOUTUBE_DOMAINS.any { domain ->
                normalizedHost == domain || normalizedHost.endsWith(".$domain")
            }
        }

        private val DIRECT = listOf(Proxy.NO_PROXY)
        private val YOUTUBE_DOMAINS = setOf(
            "youtube.com",
            "youtube-nocookie.com",
            "youtu.be",
            "googlevideo.com",
            "ytimg.com",
            "ggpht.com",
            "googleusercontent.com",
            "googleapis.com",
        )
        private const val DEFAULT_HTTP_PORT = 80
    }
}
