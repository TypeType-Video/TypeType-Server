package dev.typetype.server

import dev.typetype.server.models.NotificationItem
import dev.typetype.server.models.VideoItem
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val serializationJson: Json = Json { encodeDefaults = true }

class NotificationItemPublishedAtSerializationTest {
    @Test
    fun `serialization includes publishedAt`() {
        val item = NotificationItem(
            type = "subscription_new_video",
            title = "title",
            createdAt = 1_700_000_000_123L,
            publishedAt = 1_700_000_000_123L,
            channelUrl = "https://yt.com/c/a",
            channelName = "A",
            channelAvatarUrl = "",
            serviceId = 0,
            serviceName = "YouTube",
            video = VideoItem(
                id = "id",
                title = "video",
                url = "https://yt.com/watch?v=id",
                thumbnailUrl = "",
                uploaderName = "A",
                uploaderUrl = "https://yt.com/c/a",
                uploaderAvatarUrl = "",
                duration = 1,
                viewCount = 0,
                uploadDate = "",
                uploaded = 1_700_000_000_123L,
                streamType = "video",
                isShortFormContent = false,
                uploaderVerified = false,
                shortDescription = null,
                publishedAt = 1_700_000_000_123L,
            ),
        )
        val json = serializationJson.encodeToString(NotificationItem.serializer(), item)
        assertTrue(json.contains("\"publishedAt\":1700000000123"))
    }
}
