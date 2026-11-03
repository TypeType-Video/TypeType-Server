package dev.typetype.server.services

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object BilibiliAkamaiConfig {
    const val BLACKLIST_ENV = "BILIBILI_BLACKLIST_AKAMAI_CDN"

    private val TRUE_VALUES = setOf("1", "true", "yes", "on")

    fun isAkamaiBlacklisted(getEnv: (String) -> String? = { System.getenv(it) ?: System.getProperty(it) }): Boolean =
        getEnv(BLACKLIST_ENV)?.trim()?.lowercase() in TRUE_VALUES

    fun isAkamaiHost(host: String): Boolean = host.endsWith(".akamaized.net", ignoreCase = true)

    fun isAkamaiUrl(url: String): Boolean =
        url.toHttpUrlOrNull()?.host?.let(::isAkamaiHost) == true
}
