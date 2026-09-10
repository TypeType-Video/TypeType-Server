package dev.typetype.server

import dev.typetype.server.models.UnifiedPushNotificationPayload
import dev.typetype.server.services.PushCandidate
import dev.typetype.server.services.PushNotificationSupport
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
}
