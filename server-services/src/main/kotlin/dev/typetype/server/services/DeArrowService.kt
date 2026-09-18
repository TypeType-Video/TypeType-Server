package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.DeArrowItem
import dev.typetype.server.models.DeArrowThumbnailCandidate
import dev.typetype.server.models.DeArrowTitleCandidate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

class DeArrowService(
    private val cache: CacheService,
    private val client: DeArrowRemote = DeArrowClient(),
) {
    suspend fun get(videoId: String): DeArrowItem? {
        if (!isValidVideoId(videoId)) return null
        cache.get("dearrow:branding:v2:$videoId")?.let {
            return runCatching { CacheJson.decodeFromString(DeArrowItem.serializer(), it) }.getOrNull()
        }
        val item = client.branding(videoId)?.let { parse(videoId, it) } ?: DeArrowItem(videoId)
        cache.set(
            "dearrow:branding:v2:$videoId",
            CacheJson.encodeToString(DeArrowItem.serializer(), item),
            BRANDING_TTL_SECONDS,
        )
        return item
    }

    suspend fun thumbnail(videoId: String, timestamp: Double): ByteArray? {
        if (!isValidVideoId(videoId) || !timestamp.isFinite() || timestamp < 0.0) return null
        val normalizedTime = "%.3f".format(java.util.Locale.ROOT, timestamp)
        val key = "dearrow:thumbnail:$videoId:$normalizedTime"
        cache.get(key)?.let { return runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
        val bytes = client.thumbnail(videoId, timestamp)?.takeIf { it.size <= MAX_THUMBNAIL_BYTES } ?: return null
        cache.set(key, Base64.getEncoder().encodeToString(bytes), THUMBNAIL_TTL_SECONDS)
        return bytes
    }

    private fun parse(videoId: String, raw: String): DeArrowItem {
        val root = runCatching { CacheJson.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return DeArrowItem(videoId)
        val titles = titleCandidates(root["titles"] as? JsonArray)
        val thumbnails = thumbnailCandidates(videoId, root["thumbnails"] as? JsonArray)
        val title = titles.firstOrNull { it.accepted() && !it.original }?.title
        val timestamp = thumbnails.firstOrNull { it.accepted() && !it.original }?.timestamp
            ?: randomTimestamp(root)
        val thumbnailUrl = timestamp?.takeIf { it > 0.0 }?.let { "/dearrow/thumbnail?videoId=$videoId&time=$it" }
        return DeArrowItem(
            videoId = videoId,
            title = title,
            thumbnailUrl = thumbnailUrl,
            titles = titles,
            thumbnails = thumbnails,
            randomTime = root["randomTime"]?.jsonPrimitive?.doubleOrNull,
            videoDuration = root["videoDuration"]?.jsonPrimitive?.doubleOrNull,
        )
    }

    private fun entries(entries: JsonArray?): List<JsonObject> = entries
        ?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
        .orEmpty()

    private fun titleCandidates(entries: JsonArray?): List<DeArrowTitleCandidate> = entries(entries).mapNotNull { entry ->
        val title = entry["title"]?.jsonPrimitive?.content
            ?.replace(TITLE_MARKER_REGEX, "")
            ?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        DeArrowTitleCandidate(
            title = title,
            original = entry.original(),
            votes = entry.votes(),
            locked = entry.locked(),
            uuid = entry.uuid(),
        )
    }

    private fun thumbnailCandidates(videoId: String, entries: JsonArray?): List<DeArrowThumbnailCandidate> =
        entries(entries).map { entry ->
            val timestamp = entry["timestamp"]?.jsonPrimitive?.doubleOrNull
            DeArrowThumbnailCandidate(
                timestamp = timestamp,
                thumbnailUrl = timestamp?.takeIf { it > 0.0 }?.let { "/dearrow/thumbnail?videoId=$videoId&time=$it" },
                original = entry.original(),
                votes = entry.votes(),
                locked = entry.locked(),
                uuid = entry.uuid(),
            )
        }

    private fun JsonObject.original(): Boolean = this["original"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

    private fun JsonObject.locked(): Boolean = this["locked"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

    private fun JsonObject.votes(): Int = this["votes"]?.jsonPrimitive?.intOrNull ?: -1

    private fun JsonObject.uuid(): String = this["UUID"]?.jsonPrimitive?.content.orEmpty()

    private fun DeArrowTitleCandidate.accepted(): Boolean = locked || votes >= 0

    private fun DeArrowThumbnailCandidate.accepted(): Boolean = locked || votes >= 0

    private fun randomTimestamp(root: JsonObject): Double? {
        val duration = root["videoDuration"]?.jsonPrimitive?.doubleOrNull ?: return null
        val randomTime = root["randomTime"]?.jsonPrimitive?.doubleOrNull ?: return null
        return duration * randomTime
    }

    fun isValidVideoId(videoId: String): Boolean = VIDEO_ID_REGEX.matches(videoId)

    companion object {
        private const val BRANDING_TTL_SECONDS = 86_400L
        private const val THUMBNAIL_TTL_SECONDS = 604_800L
        private const val MAX_THUMBNAIL_BYTES = 2 * 1024 * 1024
        private val VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")
        private val TITLE_MARKER_REGEX = Regex(">(?=\\S)")
    }
}
