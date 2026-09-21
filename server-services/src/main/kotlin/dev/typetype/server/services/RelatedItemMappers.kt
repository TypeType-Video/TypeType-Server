package dev.typetype.server.services

import dev.typetype.server.models.SponsorBlockSegmentItem
import dev.typetype.server.models.VideoItem
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockAction
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment
import org.schabi.newpipe.extractor.stream.StreamInfoItem

private val JAPANESE_DATE_REGEX = Regex("""\d{4}年\d{2}月\d{2}日 \d{2}[：:]\d{2}[：:]\d{2}""")

private fun String.extractJapaneseDate(): String = JAPANESE_DATE_REGEX.find(this)?.value ?: this

fun String?.toAbsoluteUrl(): String = if (this?.startsWith("http") == true) this else ""

fun SponsorBlockSegment.toSegmentItem(): SponsorBlockSegmentItem = SponsorBlockSegmentItem(
    startTime = startTime,
    endTime = endTime,
    category = category.apiName,
    action = action.apiName,
)

fun List<SponsorBlockSegmentItem>.toSponsorBlockSegments(): Array<SponsorBlockSegment> =
    mapNotNull { item ->
        runCatching {
            SponsorBlockSegment(
                "",
                item.startTime,
                item.endTime,
                SponsorBlockCategory.fromApiName(item.category),
                SponsorBlockAction.fromApiName(item.action),
                0,
            )
        }.getOrNull()
    }.toTypedArray()

fun StreamInfoItem.toVideoItem(fallbackAvatarUrl: String = ""): VideoItem {
    val uploaded = uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() ?: -1L
    val apiStreamType = streamType.toApiStreamType("video")
    return VideoItem(
        id = url ?: "",
        title = name ?: "",
        url = url ?: "",
        thumbnailUrl = thumbnailUrl.toAbsoluteUrl(),
        uploaderName = uploaderName ?: "",
        uploaderUrl = uploaderUrl ?: "",
        uploaderAvatarUrl = (uploaderAvatarUrl?.takeIf { it.isNotEmpty() } ?: fallbackAvatarUrl).replace("httpss://", "https://"),
        duration = duration,
        viewCount = viewCount,
        uploadDate = textualUploadDate?.extractJapaneseDate() ?: "",
        uploaded = uploaded,
        streamType = apiStreamType,
        isLive = apiStreamType.isLiveStreamType(),
        isPostLive = apiStreamType.isPostLiveStreamType(),
        isLiveContent = apiStreamType.isLiveContentType(),
        isShortFormContent = isShortFormContent,
        requiresMembership = requiresMembership(),
        uploaderVerified = isUploaderVerified,
        shortDescription = shortDescription?.takeIf { it.isNotBlank() },
        publishedAt = PublishedAtMapper.fromUploaded(uploaded),
    )
}

fun VideoItem.toShortCanonicalUrl(): VideoItem {
    val id = extractYouTubeVideoId(url) ?: return this
    if (url.contains("/shorts/", ignoreCase = true)) return this
    return copy(url = "https://www.youtube.com/shorts/$id")
}

fun VideoItem.toShortDedupKey(): String {
    val urlId = extractYouTubeVideoId(url)
    if (urlId != null) return urlId
    val idId = extractYouTubeVideoId(id)
    if (idId != null) return idId
    return if (url.isNotBlank()) url else id
}

private fun extractYouTubeVideoId(url: String): String? {
    val shortsMarker = "/shorts/"
    val shortsStart = url.indexOf(shortsMarker)
    if (shortsStart != -1) {
        val value = url.substring(shortsStart + shortsMarker.length)
        return value.substringBefore('?').substringBefore('&').substringBefore('#').takeIf { it.isNotBlank() }
    }
    val shortHostMarker = "youtu.be/"
    val shortHostStart = url.indexOf(shortHostMarker)
    if (shortHostStart != -1) {
        val value = url.substring(shortHostStart + shortHostMarker.length)
        return value.substringBefore('?').substringBefore('&').substringBefore('#').takeIf { it.isNotBlank() }
    }
    val marker = "v="
    val start = url.indexOf(marker)
    if (start == -1) return null
    val valueStart = start + marker.length
    val value = url.substring(valueStart)
    return value.substringBefore('&').substringBefore('#').takeIf { it.isNotBlank() }
}
