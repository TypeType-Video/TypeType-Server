package dev.typetype.server.services

object BilibiliCdnConfig {
    const val CDN_REGION_ENV = "BILIBILI_CDN_REGION"
    const val SERVER_REGION_ENV = "BILIBILI_SERVER_REGION"

    fun defaultRegion(getEnv: (String) -> String? = { System.getenv(it) ?: System.getProperty(it) }): String {
        val region = getEnv(CDN_REGION_ENV) ?: getEnv(SERVER_REGION_ENV)
        return when (region?.trim()?.lowercase()) {
            "domestic", "cn", "china", "mainland" -> "domestic"
            else -> "overseas"
        }
    }

    fun resolveRegion(clientRegion: String?, getEnv: (String) -> String? = { System.getenv(it) ?: System.getProperty(it) }): String {
        return when (clientRegion?.trim()?.lowercase()) {
            "domestic", "cn", "mainland" -> "domestic"
            "overseas", "oversea", "ov", "foreign" -> "overseas"
            else -> defaultRegion(getEnv)
        }
    }
}
