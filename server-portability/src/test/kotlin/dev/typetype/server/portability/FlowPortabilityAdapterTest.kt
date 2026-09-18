package dev.typetype.server.portability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

class FlowPortabilityAdapterTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `adapter preserves supported flow version two data`() {
        val file = directory.resolve("flow.json")
        Files.writeString(
            file,
            """
            {
              "version":2,"timestamp":123,
              "viewHistory":[{"videoId":"v1","position":12000,"duration":60000,"timestamp":10,"title":"One","channelId":"UC1"}],
              "searchHistory":[{"query":"query","timestamp":11,"type":"TEXT"}],
              "subscriptions":[{"channelId":"UC1","channelName":"Channel","channelThumbnail":"avatar","subscribedAt":12}],
              "playlists":[{"id":"p1","name":"Saved","description":"List","createdAt":13}],
              "playlistVideos":[{"playlistId":"p1","videoId":"v1","position":0,"addedAt":14}],
              "videos":[{"id":"v1","title":"One","channelId":"UC1"}],
              "subscriptionGroups":[{"name":"News","channelIds":"UC1","sortOrder":0}],
              "likedVideos":[{"videoId":"v1","title":"One","likedAt":15}],
              "contentPreferences":{"blockedChannels":["UC2"],"preferredTopics":[],"blockedTopics":[]}
            }
            """.trimIndent(),
        )
        val input = PortabilityInputFactory.create(file, file.fileName.toString(), "application/json")
        val spool = PortabilitySpool.create(directory)
        val adapter = FlowPortabilityAdapter()

        assertEquals(PortabilityFormat.FLOW, requireNotNull(adapter.detect(input)).format)
        adapter.decode(input, spool)
        assertEquals(1L, spool.counts()[PortabilityCategory.SUBSCRIPTIONS])
        assertEquals(2L, spool.counts()[PortabilityCategory.SUBSCRIPTION_GROUPS])
        assertEquals(1L, spool.counts()[PortabilityCategory.HISTORY])
        assertEquals(1L, spool.counts()[PortabilityCategory.PROGRESS])
        assertEquals(2L, spool.counts()[PortabilityCategory.PLAYLISTS])
        assertEquals(1L, spool.counts()[PortabilityCategory.FAVORITES])

        val output = ByteArrayOutputStream()
        adapter.encode(spool, output, spool.categories() - PortabilityCategory.PLAYLISTS)
        assertTrue(output.toString().contains("\"version\":2"))
        assertTrue(output.toString().contains("\"channelIds\":\"UC1\""))
        spool.delete()
    }
}
