package dev.typetype.server.ai

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

object AiOutboundProxy {
    fun fromUrl(proxyUrl: String?): Proxy? {
        val value = proxyUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val uri = runCatching { URI(value) }
            .getOrElse { throw IllegalArgumentException("Invalid AI outbound proxy URL", it) }
        val scheme = uri.scheme?.lowercase()
        val (proxyType, defaultPort) = when (scheme) {
            "http" -> Proxy.Type.HTTP to DEFAULT_HTTP_PORT
            "socks5" -> Proxy.Type.SOCKS to DEFAULT_SOCKS5_PORT
            else -> throw IllegalArgumentException(
                "AI_OUTBOUND_PROXY_URL must use the http or socks5 scheme",
            )
        }
        val host = uri.host?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("AI_OUTBOUND_PROXY_URL must include a host")
        require(uri.userInfo == null && uri.path.isNullOrEmpty() && uri.query == null && uri.fragment == null) {
            "AI_OUTBOUND_PROXY_URL must contain only a scheme, host, and port"
        }
        require(!uri.rawAuthority.orEmpty().endsWith(":")) {
            "AI_OUTBOUND_PROXY_URL must not end with an empty port"
        }
        val port = if (uri.port == -1) {
            defaultPort
        } else {
            require(uri.port in 1..MAX_PORT) {
                "AI_OUTBOUND_PROXY_URL port must be between 1 and 65535"
            }
            uri.port
        }
        val address = InetSocketAddress.createUnresolved(host, port)
        return Proxy(proxyType, address)
    }

    private const val DEFAULT_HTTP_PORT = 80
    private const val DEFAULT_SOCKS5_PORT = 1080
    private const val MAX_PORT = 65_535
}
