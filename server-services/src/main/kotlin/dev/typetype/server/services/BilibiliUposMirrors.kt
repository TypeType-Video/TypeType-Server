package dev.typetype.server.services

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val UPOS_MIRROR_HOST = Regex("""upos-[a-z]+-mirror[a-z0-9]+\.(bilivideo\.com|akamaized\.net)""")

private val UPOS_MIRROR_HOSTS = listOf(
    "upos-sz-mirrorcos.bilivideo.com",
    "upos-sz-mirrorali.bilivideo.com",
    "upos-sz-mirrorhw.bilivideo.com",
    "upos-hz-mirrorakam.akamaized.net",
)

fun bilibiliUposMirrorAlternates(url: String): List<String> {
    val parsed = url.toHttpUrlOrNull() ?: return emptyList()
    if (!UPOS_MIRROR_HOST.matches(parsed.host)) return emptyList()
    return UPOS_MIRROR_HOSTS.filter { it != parsed.host }.map { parsed.newBuilder().host(it).build().toString() }
}
