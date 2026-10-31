package dev.typetype.server.services

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val UPOS_MIRROR_HOST = Regex("""upos-[a-z]+-mirror[a-z0-9]+\.(bilivideo\.com|akamaized\.net)""")

val OVERSEAS_UPOS_MIRROR_HOSTS = listOf(
    "upos-sz-mirroraliov.bilivideo.com",
    "upos-sz-mirrorcosov.bilivideo.com",
)

val DOMESTIC_UPOS_MIRROR_HOSTS = listOf(
    "upos-sz-mirrorali.bilivideo.com",
    "upos-sz-mirrorhw.bilivideo.com",
    "upos-sz-mirrorcos.bilivideo.com",
)

val ALL_UPOS_MIRROR_HOSTS = listOf(
    "upos-sz-mirroraliov.bilivideo.com",
    "upos-sz-mirrorcosov.bilivideo.com",
    "upos-sz-mirrorali.bilivideo.com",
    "upos-sz-mirrorhw.bilivideo.com",
    "upos-sz-mirrorcos.bilivideo.com",
    "upos-hz-mirrorakam.akamaized.net",
)

fun isOverseasUposHost(host: String): Boolean =
    host.contains("mirroraliov", ignoreCase = true) ||
    host.contains("mirrorcosov", ignoreCase = true) ||
    host.contains("oversea", ignoreCase = true) ||
    host.contains("ov.", ignoreCase = true)

fun isDomesticUposHost(host: String): Boolean =
    !isOverseasUposHost(host) && !BilibiliAkamaiConfig.isAkamaiHost(host)

fun bilibiliUposMirrorAlternates(
    url: String,
    isAkamaiBlacklisted: Boolean = BilibiliAkamaiConfig.isAkamaiBlacklisted(),
    cdnRegion: String? = null,
): List<String> {
    val parsed = url.toHttpUrlOrNull() ?: return emptyList()
    if (!UPOS_MIRROR_HOST.matches(parsed.host)) return emptyList()
    val effectiveRegion = BilibiliCdnConfig.resolveRegion(cdnRegion)
    val hostOrder = when (effectiveRegion) {
        "domestic" -> DOMESTIC_UPOS_MIRROR_HOSTS + OVERSEAS_UPOS_MIRROR_HOSTS + listOf("upos-hz-mirrorakam.akamaized.net")
        "overseas" -> OVERSEAS_UPOS_MIRROR_HOSTS + DOMESTIC_UPOS_MIRROR_HOSTS + listOf("upos-hz-mirrorakam.akamaized.net")
        else -> ALL_UPOS_MIRROR_HOSTS
    }
    val availableHosts = if (isAkamaiBlacklisted) {
        hostOrder.filter { !BilibiliAkamaiConfig.isAkamaiHost(it) }
    } else {
        hostOrder
    }
    return availableHosts.filter { it != parsed.host }.map { parsed.newBuilder().host(it).build().toString() }
}

fun prioritizeUposMirrors(urls: List<String>, cdnRegion: String? = null): List<String> =
    BilibiliMirrorSpeedTracker.prioritize(urls, BilibiliCdnConfig.resolveRegion(cdnRegion))
