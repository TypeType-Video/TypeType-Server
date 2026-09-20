package dev.typetype.server.services

/** Validates the optional instance-wide BiliBili cookie used by extraction. */
data class BilibiliCookieConfig internal constructor(
    val cookieHeader: String?,
    val wasProvided: Boolean,
) {
    val isConfigured: Boolean
        get() = cookieHeader != null

    companion object {
        private const val MAX_COOKIE_LENGTH = 32 * 1024
        private val COOKIE_NAME = Regex("[!#\\$%&'*+.^_`|~0-9A-Za-z-]+")
        private val ACCOUNT_COOKIE_NAMES = setOf("SESSDATA", "bili_jct", "buvid3", "DedeUserID")

        fun fromEnvironment(read: (String) -> String? = System::getenv): BilibiliCookieConfig =
            fromRaw(SecretConfigReader.read("BILIBILI_COOKIE", read))

        internal fun fromRaw(raw: String?): BilibiliCookieConfig {
            val supplied = raw?.trim()?.takeIf { it.isNotEmpty() }
                ?: return BilibiliCookieConfig(cookieHeader = null, wasProvided = false)
            return BilibiliCookieConfig(cookieHeader = normalize(supplied), wasProvided = true)
        }

        private fun normalize(raw: String): String? {
            if (raw.length > MAX_COOKIE_LENGTH || raw.any { it == '\r' || it == '\n' }) return null
            val withoutHeader = if (raw.startsWith("Cookie:", ignoreCase = true)) {
                raw.substringAfter(':').trim()
            } else {
                raw
            }
            if (withoutHeader.isEmpty()) return null
            val names = HashSet<String>()
            val pairs = withoutHeader.split(';')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map { pair ->
                    val separator = pair.indexOf('=')
                    if (separator <= 0) return null
                    val name = pair.substring(0, separator).trim()
                    val value = pair.substring(separator + 1).trim()
                    if (!COOKIE_NAME.matches(name) || value.isEmpty() ||
                        value.any { it == '\r' || it == '\n' } || !names.add(name)
                    ) return null
                    name to value
                }
            if (pairs.isEmpty() || pairs.none { it.first in ACCOUNT_COOKIE_NAMES }) return null
            return pairs.joinToString("; ") { (name, value) -> "$name=$value" }
        }
    }
}
