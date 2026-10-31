package dev.typetype.server.services

object BilibiliAkamaiConfig {
    const val BLACKLIST_ENV = "BILIBILI_BLACKLIST_AKAMAI_CDN"
    const val DISABLE_ENV = "BILIBILI_DISABLE_AKAMAI_CDN"
    const val ENABLE_ENV = "BILIBILI_AKAMAI_CDN"

    private val TRUE_VALUES = setOf("1", "true", "yes", "on")
    private val FALSE_VALUES = setOf("0", "false", "no", "off")

    /**
     * Determines whether Akamai CDN is blacklisted / excluded from Bilibili video streaming.
     * Users can freely configure this in their Docker Compose or Docker run environment:
     * - `BILIBILI_BLACKLIST_AKAMAI_CDN=true` (or `1`, `yes`, `on`) -> blacklisted
     * - `BILIBILI_DISABLE_AKAMAI_CDN=true` -> blacklisted
     * - `BILIBILI_AKAMAI_CDN=off` (or `false`, `0`, `no`) -> blacklisted
     *
     * Default: `false` (Akamai is not blacklisted unless configured by the user).
     */
    fun isAkamaiBlacklisted(getEnv: (String) -> String? = { System.getenv(it) ?: System.getProperty(it) }): Boolean {
        val blacklist = getEnv(BLACKLIST_ENV)?.trim()?.lowercase()
        if (blacklist != null) {
            if (blacklist in TRUE_VALUES) return true
            if (blacklist in FALSE_VALUES) return false
        }

        val disable = getEnv(DISABLE_ENV)?.trim()?.lowercase()
        if (disable != null) {
            if (disable in TRUE_VALUES) return true
            if (disable in FALSE_VALUES) return false
        }

        val enable = getEnv(ENABLE_ENV)?.trim()?.lowercase()
        if (enable != null) {
            if (enable in FALSE_VALUES) return true
            if (enable in TRUE_VALUES) return false
        }

        return false
    }

    fun isAkamaiHost(host: String): Boolean =
        host.contains("akam", ignoreCase = true) || host.contains("akamaized.net", ignoreCase = true)

    fun isAkamaiUrl(url: String): Boolean =
        url.contains("akamaized.net", ignoreCase = true) || url.contains("mirrorakam", ignoreCase = true)
}
