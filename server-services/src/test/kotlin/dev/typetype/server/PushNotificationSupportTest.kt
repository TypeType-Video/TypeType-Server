package dev.typetype.server

import dev.typetype.server.models.UnifiedPushNotificationPayload
import dev.typetype.server.services.PushCandidate
import dev.typetype.server.services.PushNotificationSupport
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PushNotificationSupportTest {
    @Test
    fun `payload identifies the provider independently of channel names`() {
        val candidate = PushCandidate(
            serviceId = 5,
            channelId = "https://www.bilibili.com/space/42",
            videoId = "BV1",
            publishedAt = 1_000L,
            video = SubscriptionFeedTestFixtures.video(
                uploaded = 1L,
                channel = "Shared name",
                url = "https://www.bilibili.com/video/BV1",
            ),
        )

        val payload = Json.decodeFromString<UnifiedPushNotificationPayload>(
            PushNotificationSupport.payload("instance", "event", candidate, "profile"),
        )
        assertEquals(5, payload.serviceId)
        assertEquals("BiliBili", payload.serviceName)
        assertEquals("event", payload.eventId)
        assertEquals("profile", payload.accountId)
    }

    @Test
    fun `payload always encodes its version and carries the video thumbnail`() {
        val video = SubscriptionFeedTestFixtures.video(
            uploaded = 1L,
            channel = "Channel",
            url = "https://www.youtube.com/watch?v=abc",
        ).copy(thumbnailUrl = "https://i.ytimg.com/vi/abc/hqdefault.jpg")
        val candidate = PushCandidate(0, "https://www.youtube.com/channel/UC1", "abc", 1_000L, video)

        val encoded = PushNotificationSupport.payload("instance", "event", candidate, "profile")
        val json = Json.parseToJsonElement(encoded).jsonObject

        assertEquals("1", json["version"]?.jsonPrimitive?.content)
        assertEquals("https://i.ytimg.com/vi/abc/hqdefault.jpg", json["thumbnailUrl"]?.jsonPrimitive?.content)
    }
}
