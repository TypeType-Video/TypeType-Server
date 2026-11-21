package dev.typetype.server.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Collections
import java.util.concurrent.TimeUnit

// b23.tv share links only reveal their video through a redirect. The server follows that single redirect itself and
// hands back the target only when it is a bilibili.com page, so the link never becomes a way to reach other hosts.
class BilibiliShortLinkResolver(
    client: OkHttpClient = OkHttpClient(),
) {
    private val client = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .build()
    private val resolved = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > CACHE_SIZE
        },
    )

    suspend fun resolve(code: String): String? {
        if (!CODE.matches(code)) return null
        resolved[code]?.let { return it }
        val target = withContext(Dispatchers.IO) { fetchTarget(code) } ?: return null
        resolved[code] = target
        return target
    }

    private fun fetchTarget(code: String): String? {
        val request = Request.Builder().url("https://$SHORT_HOST/$code").header("User-Agent", USER_AGENT).build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (response.code !in REDIRECTS) return null
                bilibiliPage(response.header("Location"))
            }
        }.getOrNull()
    }

    private fun bilibiliPage(location: String?): String? {
        val url = location?.toHttpUrlOrNull() ?: return null
        val host = url.host.lowercase()
        val bilibili = host == BILIBILI_HOST || host.endsWith(".$BILIBILI_HOST")
        if (url.scheme != "https" || !bilibili || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        return url.newBuilder().query(null).fragment(null).apply {
            url.queryParameter("p")?.let { addQueryParameter("p", it) }
        }.build().toString()
    }

    private companion object {
        val CODE = Regex("^[A-Za-z0-9]{4,16}$")
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
        const val SHORT_HOST = "b23.tv"
        const val BILIBILI_HOST = "bilibili.com"
        const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64; rv:140.0) Gecko/20100101 Firefox/140.0"
        const val TIMEOUT_S = 8L
        const val CACHE_SIZE = 512
    }
}
