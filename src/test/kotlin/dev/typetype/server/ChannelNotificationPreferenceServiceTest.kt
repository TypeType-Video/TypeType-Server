package dev.typetype.server

import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.services.ChannelNotificationPreferenceService
import dev.typetype.server.services.SubscriptionsService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ChannelNotificationPreferenceServiceTest {
    private val subscriptions = SubscriptionsService()
    private val preferences = ChannelNotificationPreferenceService(subscriptions)

    companion object {
        private const val CHANNEL_URL = "https://www.youtube.com/channel/UCNotification"
        private const val OTHER_CHANNEL_URL = "https://www.youtube.com/channel/UCOther"

        @BeforeAll
        @JvmStatic
        fun initDb(): Unit = TestDatabase.setup()
    }

    @BeforeEach
    fun clean(): Unit = TestDatabase.truncateAll()

    @Test
    fun `channel notifications are disabled until explicitly enabled`() = runTest {
        subscriptions.add(TEST_USER_ID, SubscriptionItem(CHANNEL_URL, "Channel", ""))

        assertEquals(false, preferences.list(TEST_USER_ID).single().enabled)

        preferences.set(TEST_USER_ID, CHANNEL_URL, true)

        assertEquals(true, preferences.list(TEST_USER_ID).single().enabled)
    }

    @Test
    fun `bulk update applies to every subscribed channel in one call`() = runTest {
        subscriptions.add(TEST_USER_ID, SubscriptionItem(CHANNEL_URL, "Channel", ""))
        subscriptions.add(TEST_USER_ID, SubscriptionItem(OTHER_CHANNEL_URL, "Other", ""))

        val updated = preferences.setAll(TEST_USER_ID, enabled = true, rawChannelUrls = null)

        assertEquals(2, updated.size)
        assertEquals(listOf(true, true), preferences.list(TEST_USER_ID).map { it.enabled })
    }

    @Test
    fun `bulk update ignores channels that are not subscribed`() = runTest {
        subscriptions.add(TEST_USER_ID, SubscriptionItem(CHANNEL_URL, "Channel", ""))
        preferences.setAll(TEST_USER_ID, enabled = true, rawChannelUrls = null)

        val updated = preferences.setAll(
            TEST_USER_ID,
            enabled = false,
            rawChannelUrls = listOf(CHANNEL_URL, "https://www.youtube.com/channel/UCUnknown"),
        )

        assertEquals(listOf(CHANNEL_URL), updated.map { it.channelUrl })
        assertEquals(false, preferences.list(TEST_USER_ID).single().enabled)
    }
}
