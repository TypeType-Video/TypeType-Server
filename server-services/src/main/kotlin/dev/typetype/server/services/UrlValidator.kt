package dev.typetype.server.services

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress
import java.net.URI

enum class ProxyProvider {
    YOUTUBE,
    BILIBILI,
    NICONICO,
}

data class ProxyTarget(
    val url: HttpUrl,
    val provider: ProxyProvider,
)

class ProxyTargetRejectedException(message: String) : IllegalArgumentException(message)

private val BLOCKED_IPV4_RANGES = listOf(
    ipv4(0, 0, 0, 0) to 8,
    ipv4(10, 0, 0, 0) to 8,
    ipv4(100, 64, 0, 0) to 10,
    ipv4(127, 0, 0, 0) to 8,
    ipv4(169, 254, 0, 0) to 16,
    ipv4(172, 16, 0, 0) to 12,
    ipv4(192, 0, 0, 0) to 24,
    ipv4(192, 0, 2, 0) to 24,
    ipv4(192, 168, 0, 0) to 16,
    ipv4(198, 18, 0, 0) to 15,
    ipv4(198, 51, 100, 0) to 24,
    ipv4(203, 0, 113, 0) to 24,
    ipv4(224, 0, 0, 0) to 4,
    ipv4(240, 0, 0, 0) to 4,
)

private val BLOCKED_IPV6_RANGES = listOf(
    byteArrayOf(0x20, 0x01, 0x00, 0x00) to 32,
    byteArrayOf(0x20, 0x01, 0x00, 0x02, 0x00, 0x00) to 48,
    byteArrayOf(0x20, 0x01, 0x00, 0x10) to 28,
    byteArrayOf(0x20, 0x01, 0x00, 0x20) to 28,
    byteArrayOf(0x20, 0x01, 0x0D, 0xB8.toByte()) to 32,
    byteArrayOf(0x20, 0x02) to 16,
)

fun validateProxyUrl(raw: String): String? =
    runCatching { requireProxyTarget(raw) }
        .exceptionOrNull()
        ?.message

fun requireProxyTarget(raw: String): ProxyTarget {
    val uri = runCatching { URI(raw) }.getOrElse { throw ProxyTargetRejectedException("Malformed URL") }
    val scheme = uri.scheme?.lowercase() ?: throw ProxyTargetRejectedException("Missing URL scheme")
    if (scheme != "https") throw ProxyTargetRejectedException("Unsupported URL scheme: $scheme")
    if (uri.rawUserInfo != null) throw ProxyTargetRejectedException("URL credentials are not allowed")
    if (uri.host == null) throw ProxyTargetRejectedException("Missing URL host")
    val url = raw.toHttpUrlOrNull() ?: throw ProxyTargetRejectedException("Malformed URL")
    if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
        throw ProxyTargetRejectedException("URL credentials are not allowed")
    }
    if (url.port != 443) throw ProxyTargetRejectedException("Unsupported proxy port")
    val provider = providerForProxyHost(url.host)
        ?: throw ProxyTargetRejectedException("Unsupported proxy host")
    return ProxyTarget(url, provider)
}

fun providerForProxyHost(rawHost: String): ProxyProvider? {
    val host = rawHost.lowercase().trimEnd('.')
    return when {
        host.matchesHost("googlevideo.com") ||
            host.matchesHost("ytimg.com") ||
            host.matchesHost("ggpht.com") ||
            host == "yt3.googleusercontent.com" -> ProxyProvider.YOUTUBE
        host.matchesHost("bilivideo.com") ||
            host.matchesHost("bilivideo.cn") ||
            host.matchesHost("hdslb.com") ||
            host == "upos-hz-mirrorakam.akamaized.net" -> ProxyProvider.BILIBILI
        host.matchesHost("nicovideo.jp") || host.matchesHost("nimg.jp") -> ProxyProvider.NICONICO
        else -> null
    }
}

fun isPublicProxyAddress(address: InetAddress): Boolean {
    if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
        address.isSiteLocalAddress || address.isMulticastAddress
    ) return false
    val bytes = address.address
    return when (bytes.size) {
        4 -> BLOCKED_IPV4_RANGES.none { (prefix, bits) -> hasPrefix(bytes, prefix, bits) }
        16 -> isPublicIpv6(bytes)
        else -> false
    }
}

private fun isPublicIpv6(bytes: ByteArray): Boolean {
    if ((bytes[0].toInt() and 0xE0) != 0x20) return false
    return BLOCKED_IPV6_RANGES.none { (prefix, bits) -> hasPrefix(bytes, prefix, bits) }
}

private fun String.matchesHost(suffix: String): Boolean = this == suffix || endsWith(".$suffix")

private fun ipv4(a: Int, b: Int, c: Int, d: Int): ByteArray =
    byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte())

private fun hasPrefix(address: ByteArray, prefix: ByteArray, bits: Int): Boolean {
    val fullBytes = bits / 8
    for (index in 0 until fullBytes) if (address[index] != prefix[index]) return false
    val remaining = bits % 8
    if (remaining == 0) return true
    val mask = 0xFF shl (8 - remaining)
    return (address[fullBytes].toInt() and mask) == (prefix[fullBytes].toInt() and mask)
}
