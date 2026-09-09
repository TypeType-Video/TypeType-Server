package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.models.StreamResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.bilibili.BilibiliService
import java.time.Duration

private const val RELATED_BASE_URL = "https://api.bilibili.com/x/web-interface/archive/related?bvid="
private const val SPACE_BASE_URL = "https://space.bilibili.com/"
private val BVID_REGEX = Regex("""/(BV[0-9A-Za-z]+)""")

internal class BilibiliRelatedService(
    private val relatedLookupOverride: (suspend (String) -> Map<String, String>)? = null,
) {
    private val uploaderUrlCache = BoundedExpiringCache<String, Map<String, String>>(
        maxEntries = 256,
        ttl = Duration.ofMinutes(5),
        weigher = { it.size.toLong().coerceAtLeast(1L) },
    )

    suspend fun patchRelatedStreams(response: StreamResponse, videoUrl: String): StreamResponse {
        val sourceBvid = BVID_REGEX.find(videoUrl)?.groupValues?.get(1) ?: return response
        val missingRelatedBvids = response.relatedStreams.asSequence()
            .filter { it.uploaderUrl.isBlank() }
            .mapNotNull { BVID_REGEX.find(it.url)?.groupValues?.get(1) }
            .toSet()
        if (missingRelatedBvids.isEmpty()) return response
        val uploaderUrls = uploaderUrlCache.get(sourceBvid) ?: resolveUploaderUrls(videoUrl).also { fetched ->
            if (fetched.isNotEmpty()) uploaderUrlCache.put(sourceBvid, fetched)
        }
        if (uploaderUrls.isEmpty()) return response
        return response.copy(
            relatedStreams = response.relatedStreams.map { item ->
                if (item.uploaderUrl.isNotBlank()) item
                else {
                    val bvid = BVID_REGEX.find(item.url)?.groupValues?.get(1)
                    if (bvid in missingRelatedBvids) {
                        uploaderUrls[bvid]?.let { item.copy(uploaderUrl = it) } ?: item
                    } else item
                }
            }
        )
    }

    private suspend fun resolveUploaderUrls(videoUrl: String): Map<String, String> =
        relatedLookupOverride?.invoke(videoUrl) ?: fetchUploaderUrls(videoUrl)

    private suspend fun fetchUploaderUrls(videoUrl: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bvid = BVID_REGEX.find(videoUrl)?.groupValues?.get(1)
                    ?: return@runCatching emptyMap()
                val url = RELATED_BASE_URL + bvid
                val headers = BilibiliService.getHeaders(url)
                val body = NewPipe.getDownloader().get(url, headers).responseBody()
                val root = CacheJson.parseToJsonElement(body).jsonObject
                val data = root["data"]?.jsonArray ?: return@runCatching emptyMap()
                buildMap {
                    for (el in data) {
                        val item = el.jsonObject
                        val relatedBvid = item["bvid"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                            ?: continue
                        val mid = item["owner"]?.jsonObject?.get("mid")?.jsonPrimitive?.longOrNull ?: continue
                        if (mid > 0L) put(relatedBvid, "$SPACE_BASE_URL$mid")
                    }
                }
            }.getOrElse { emptyMap() }
        }
}
