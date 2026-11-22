package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.bilibili.BilibiliService
import java.time.Duration

const val MAX_VIDEO_TITLE_IDS = 30

private val BVID = Regex("""BV[0-9A-Za-z]{10}""")
private val AVID = Regex("""(?i)av([1-9][0-9]{0,14})""")
private val NICONICO_ID = Regex("""(?i)(sm|so|nm)([1-9][0-9]{0,11})""")
private val XML_TITLE = Regex("""<title>([^<]*)</title>""")
private const val BILIBILI_VIEW_URL = "https://api.bilibili.com/x/web-interface/view"
private const val NICONICO_THUMB_URL = "https://ext.nicovideo.jp/api/getthumbinfo/"
private const val PARALLEL_LOOKUPS = 4

// Comments and danmaku name other videos by their bare id; this turns those ids into titles for the web to show.
class VideoTitleService(
    private val fetch: suspend (String) -> String? = ::fetchUpstream,
    clock: () -> Long = System::currentTimeMillis,
) {
    // A missing title is cached too, as an empty string, so a dead id is not asked again on every render.
    private val cache = BoundedExpiringCache<String, String>(maxEntries = 4096, ttl = Duration.ofHours(12), clock = clock)
    private val lookups = Semaphore(PARALLEL_LOOKUPS)

    suspend fun titles(rawIds: List<String>): Map<String, String> = coroutineScope {
        rawIds.mapNotNull(::normalizeVideoId).distinct().take(MAX_VIDEO_TITLE_IDS)
            .map { id -> async { id to (cache.get(id) ?: lookup(id)) } }
            .awaitAll()
            .filter { (_, title) -> title.isNotEmpty() }
            .toMap()
    }

    private suspend fun lookup(id: String): String {
        val title = lookups.withPermit { runCatching { fetch(id) }.getOrNull() }?.trim().orEmpty()
        cache.put(id, title)
        return title
    }
}

fun normalizeVideoId(raw: String): String? {
    val id = raw.trim()
    BVID.matchEntire(id)?.let { return it.value }
    AVID.matchEntire(id)?.let { return "av" + it.groupValues[1] }
    NICONICO_ID.matchEntire(id)?.let { return it.groupValues[1].lowercase() + it.groupValues[2] }
    return null
}

private suspend fun fetchUpstream(id: String): String? = withContext(Dispatchers.IO) {
    val downloader = NewPipe.getDownloader()
    when {
        id.startsWith("BV") || id.startsWith("av") -> {
            val query = if (id.startsWith("BV")) "bvid=$id" else "aid=${id.removePrefix("av")}"
            val url = "$BILIBILI_VIEW_URL?$query"
            val body = downloader.get(url, BilibiliService.getHeaders(url)).responseBody()
            CacheJson.parseToJsonElement(body).jsonObject["data"]?.jsonObject?.get("title")?.jsonPrimitive?.content
        }
        else -> {
            val body = downloader.get(NICONICO_THUMB_URL + id).responseBody()
            if (!body.contains("status=\"ok\"")) null else XML_TITLE.find(body)?.groupValues?.get(1)?.let(::unescapeXml)
        }
    }
}

private fun unescapeXml(text: String): String = text
    .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'")
    .replace("&amp;", "&")
