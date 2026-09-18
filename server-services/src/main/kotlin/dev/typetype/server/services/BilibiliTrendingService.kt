package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.bilibili.BilibiliService
import java.text.SimpleDateFormat
import java.util.Date

private const val RECOMMENDED_URL = "https://api.bilibili.com/x/web-interface/index/top/rcmd?fresh_type=3"
private const val SPACE_URL = "https://space.bilibili.com/"

class BilibiliTrendingService {

    @Suppress("SimpleDateFormat")
    private fun formatDate(pubdate: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(pubdate * 1000L))

    suspend fun getTrending(): ExtractionResult<List<VideoItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val headers = BilibiliService.getHeaders(RECOMMENDED_URL)
                val body = NewPipe.getDownloader().get(RECOMMENDED_URL, headers).responseBody()
                val root = CacheJson.parseToJsonElement(body).jsonObject
                val items = root["data"]?.jsonObject?.get("item")?.jsonArray
                    ?: return@runCatching emptyList<VideoItem>()
                items.map { el ->
                    val item = el.jsonObject
                    val owner = item["owner"]?.jsonObject
                    val mid = owner?.get("mid")?.jsonPrimitive?.longOrNull ?: 0L
                    val pic = item["pic"]?.jsonPrimitive?.content ?: ""
                    val pubdate = item["pubdate"]?.jsonPrimitive?.longOrNull ?: 0L
                    val uploaded = if (pubdate > 0L) pubdate * 1000L else -1L
                    val uri = item["uri"]?.jsonPrimitive?.content ?: ""
                    val bvid = item["bvid"]?.jsonPrimitive?.content ?: ""
                    val url = if (uri.isNotBlank()) "$uri?p=1" else "https://www.bilibili.com/video/$bvid?p=1"
                    VideoItem(
                        id = url,
                        title = item["title"]?.jsonPrimitive?.content ?: "",
                        url = url,
                        thumbnailUrl = pic.replace("http:", "https:"),
                        uploaderName = owner?.get("name")?.jsonPrimitive?.content ?: "",
                        uploaderUrl = if (mid > 0L) "$SPACE_URL$mid" else "",
                        uploaderAvatarUrl = owner?.get("face")?.jsonPrimitive?.content ?: "",
                        duration = item["duration"]?.jsonPrimitive?.longOrNull ?: 0L,
                        viewCount = item["stat"]?.jsonObject?.get("view")?.jsonPrimitive?.long ?: 0L,
                        uploadDate = if (pubdate > 0L) formatDate(pubdate) else "",
                        uploaded = uploaded,
                        streamType = "video",
                        isShortFormContent = false,
                        uploaderVerified = false,
                        shortDescription = null,
                        publishedAt = PublishedAtMapper.fromUploaded(uploaded),
                    )
                }
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "BiliBili trending failed") }
            )
        }
}
