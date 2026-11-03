package dev.typetype.server.services

object BilibiliCdnConfig {
    const val CDN_REGION_ENV = "BILIBILI_CDN_REGION"
    const val SUBCHUNK_PROXY_ENV = "BILIBILI_SUBCHUNK_PROXY"
    const val CONCURRENT_PREFETCH_ENV = "BILIBILI_CONCURRENT_PREFETCH"

    private val TRUE_VALUES = setOf("1", "true", "yes", "on")

    fun defaultRegion(getEnv: (String) -> String? = { System.getenv(it) ?: System.getProperty(it) }): String? =
        when (getEnv(CDN_REGION_ENV)?.trim()?.lowercase()) {
            "overseas", "oversea", "ov", "foreign" -> "overseas"
            "domestic", "cn", "china", "mainland" -> "domestic"
            else -> null
        }

    fun resolveRegion(
        clientRegion: String? = null,
        getEnv: (String) -> String? = { System.getenv(it) ?: System.getProperty(it) },
    ): String? = when (clientRegion?.trim()?.lowercase()) {
        "overseas", "oversea", "ov", "foreign" -> "overseas"
        "domestic", "cn", "china", "mainland" -> "domestic"
        else -> defaultRegion(getEnv)
    }

    fun isPrefetchEnabled(getEnv: (String) -> String? = { System.getenv(it) ?: System.getProperty(it) }): Boolean =
        getEnv(CONCURRENT_PREFETCH_ENV)?.trim()?.lowercase() in TRUE_VALUES

    fun isSubchunkProxyEnabled(getEnv: (String) -> String? = { System.getenv(it) ?: System.getProperty(it) }): Boolean =
        getEnv(SUBCHUNK_PROXY_ENV)?.trim()?.lowercase() in TRUE_VALUES ||
            isPrefetchEnabled(getEnv)
}
