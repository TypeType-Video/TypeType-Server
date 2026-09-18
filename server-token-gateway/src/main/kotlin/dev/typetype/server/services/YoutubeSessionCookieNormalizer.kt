package dev.typetype.server.services

object YoutubeSessionCookieNormalizer {
    private const val MAX_RAW_COOKIE_LENGTH = 1024 * 1024
    private val allowedNames = setOf(
        "SID",
        "HSID",
        "SSID",
        "APISID",
        "SAPISID",
        "LOGIN_INFO",
        "SIDCC",
        "__Secure-1PSID",
        "__Secure-3PSID",
        "__Secure-1PAPISID",
        "__Secure-3PAPISID",
        "__Secure-1PSIDCC",
        "__Secure-3PSIDCC",
        "__Secure-1PSIDTS",
        "__Secure-3PSIDTS",
        "__Host-1PLSID",
        "__Host-3PLSID",
        "AEC",
        "NID",
        "PREF",
        "SOCS",
        "VISITOR_INFO1_LIVE",
        "VISITOR_PRIVACY_METADATA",
        "YSC",
    )

    fun normalize(raw: String): String? {
        if (raw.length > MAX_RAW_COOKIE_LENGTH) return null
        val cookies = if ('\t' in raw) parseNetscape(raw) else parseHeader(raw)
        val header = cookies.values
            .filter { it.name in allowedNames && it.value.isNotBlank() }
            .joinToString("; ") { "${it.name}=${it.value}" }
        return header.takeIf { it.isNotBlank() }
    }

    private fun parseHeader(raw: String): Map<String, YoutubeSessionCookie> =
        raw.trim().removePrefix("Cookie:").trim().split(';').mapNotNull { part ->
            val index = part.indexOf('=')
            if (index <= 0) return@mapNotNull null
            val name = part.take(index).trim()
            val value = part.drop(index + 1).trim()
            YoutubeSessionCookie(name = name, value = value, priority = 10)
        }.toCookieMap()

    private fun parseNetscape(raw: String): Map<String, YoutubeSessionCookie> =
        raw.lineSequence().mapNotNull { line ->
            if (line.isBlank() || line.startsWith("#")) return@mapNotNull null
            val fields = line.split('\t')
            if (fields.size < 7) return@mapNotNull null
            val priority = fields[0].domainPriority() ?: return@mapNotNull null
            YoutubeSessionCookie(name = fields[5].trim(), value = fields[6].trim(), priority = priority)
        }.toCookieMap()

    private fun Sequence<YoutubeSessionCookie>.toCookieMap(): Map<String, YoutubeSessionCookie> =
        fold(linkedMapOf()) { acc, cookie ->
            val current = acc[cookie.name]
            if (current == null || cookie.priority >= current.priority) acc[cookie.name] = cookie
            acc
        }

    private fun List<YoutubeSessionCookie>.toCookieMap(): Map<String, YoutubeSessionCookie> =
        asSequence().toCookieMap()

    private fun String.domainPriority(): Int? {
        val domain = trim().removePrefix(".").lowercase()
        return when {
            domain == "youtube.com" || domain.endsWith(".youtube.com") -> 4
            domain == "google.com" || domain.endsWith(".google.com") -> 3
            domain == "youtube-nocookie.com" || domain.endsWith(".youtube-nocookie.com") -> 2
            else -> null
        }
    }
}
