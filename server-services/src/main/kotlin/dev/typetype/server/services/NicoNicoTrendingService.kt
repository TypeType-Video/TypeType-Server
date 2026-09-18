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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.OffsetDateTime

private const val TRENDING_URL = "https://www.nicovideo.jp/ranking/genre/all?term=24h"
private const val WATCH_URL = "https://www.nicovideo.jp/watch/"
private const val USER_URL = "https://www.nicovideo.jp/user/"
private const val CHANNEL_URL = "https://ch.nicovideo.jp/"
private val META_REGEX = Regex("""name="server-response"\s+content="([^"]+)"""")

private fun htmlUnescape(s: String): String =
    s.replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&#39;", "'")

class NicoNicoTrendingService(private val httpClient: OkHttpClient) {

    suspend fun getTrending(): ExtractionResult<List<VideoItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(TRENDING_URL)
                    .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .build()
                val body = httpClient.newCall(request).execute().use { it.body.string() }
                val rawMeta = META_REGEX.find(body)?.groupValues?.get(1)
                    ?: return@runCatching emptyList<VideoItem>()
                val unescaped = htmlUnescape(rawMeta)
                val root = CacheJson.parseToJsonElement(unescaped).jsonObject
                val items = root["data"]?.jsonObject
                    ?.get("response")?.jsonObject
                    ?.get("\$getTeibanRanking")?.jsonObject
                    ?.get("data")?.jsonObject
                    ?.get("items")?.jsonArray
                    ?: return@runCatching emptyList<VideoItem>()
                items.map { el ->
                    val item = el.jsonObject
                    val owner = item["owner"]?.jsonObject
                    val ownerType = owner?.get("ownerType")?.jsonPrimitive?.content ?: "user"
                    val ownerId = owner?.get("id")?.jsonPrimitive?.content ?: ""
                    val uploaderUrl = if (ownerType == "channel") "$CHANNEL_URL$ownerId" else "$USER_URL$ownerId"
                    val registeredAt = item["registeredAt"]?.jsonPrimitive?.content ?: ""
                    val uploaded = runCatching { OffsetDateTime.parse(registeredAt).toInstant().toEpochMilli() }.getOrElse { -1L }
                    VideoItem(
                        id = WATCH_URL + (item["id"]?.jsonPrimitive?.content ?: ""),
                        title = item["title"]?.jsonPrimitive?.content ?: "",
                        url = WATCH_URL + (item["id"]?.jsonPrimitive?.content ?: ""),
                        thumbnailUrl = item["thumbnail"]?.jsonObject?.get("largeUrl")?.jsonPrimitive?.content ?: "",
                        uploaderName = owner?.get("name")?.jsonPrimitive?.content ?: "",
                        uploaderUrl = uploaderUrl,
                        uploaderAvatarUrl = owner?.get("iconUrl")?.jsonPrimitive?.content ?: "",
                        duration = item["duration"]?.jsonPrimitive?.longOrNull ?: 0L,
                        viewCount = item["count"]?.jsonObject?.get("view")?.jsonPrimitive?.long ?: 0L,
                        uploadDate = registeredAt,
                        uploaded = uploaded,
                        streamType = "video",
                        isShortFormContent = false,
                        uploaderVerified = false,
                        shortDescription = item["shortDescription"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                        publishedAt = PublishedAtMapper.fromUploaded(uploaded),
                    )
                }
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "NicoNico trending failed") }
            )
        }
}
