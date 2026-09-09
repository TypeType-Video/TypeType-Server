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

private const val RELATED_BASE_URL = "https://api.bilibili.com/x/web-interface/archive/related?bvid="
private const val SPACE_BASE_URL = "https://space.bilibili.com/"
private val BVID_REGEX = Regex("""/(BV[0-9A-Za-z]+)""")

internal class BilibiliRelatedService {

    suspend fun patchRelatedStreams(response: StreamResponse, videoUrl: String): StreamResponse {
        val missingUploaderUrls = response.relatedStreams.filter { it.uploaderUrl.isBlank() }
        if (missingUploaderUrls.isEmpty()) return response
        val relatedBvids = missingUploaderUrls.mapNotNull { BVID_REGEX.find(it.url)?.groupValues?.get(1) }
        if (relatedBvids.isEmpty()) return response
        val uploaderUrls = fetchUploaderUrls(videoUrl)
        return response.copy(
            relatedStreams = response.relatedStreams.map { item ->
                if (item.uploaderUrl.isNotBlank()) item
                else {
                    val bvid = BVID_REGEX.find(item.url)?.groupValues?.get(1) ?: ""
                    uploaderUrls[bvid]?.let { item.copy(uploaderUrl = it) } ?: item
                }
            }
        )
    }

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
