package dev.typetype.server.services

import dev.typetype.server.models.YoutubeTakeoutCategoryCounts
import dev.typetype.server.models.YoutubeTakeoutParsedData
import dev.typetype.server.models.YoutubeTakeoutPreviewItem
import dev.typetype.server.models.YoutubeTakeoutPreviewSamples
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class YoutubeTakeoutImportCacheTest {
    @Test
    fun `parsed data is bounded to one active job`() {
        val cache = YoutubeTakeoutImportCache(maxParsedEntries = 1)
        val first = parsedData("first")
        val second = parsedData("second")

        cache.setParsed("job-1", first)
        cache.setParsed("job-2", second)

        assertNull(cache.getParsed("job-1"))
        assertSame(second, cache.getParsed("job-2"))
    }

    @Test
    fun `preview and parsed data expire`() {
        var now = 100L
        val cache = YoutubeTakeoutImportCache(
            ttl = Duration.ofMillis(10),
            clock = { now },
        )
        val preview = preview()
        cache.setPreview("job", preview)
        cache.setParsed("job", parsedData("job"))

        now = 110L

        assertNull(cache.getPreview("job"))
        assertNull(cache.getParsed("job"))
    }

    @Test
    fun `remove releases both cached values`() {
        val cache = YoutubeTakeoutImportCache()
        cache.setPreview("job", preview())
        cache.setParsed("job", parsedData("job"))

        cache.remove("job")

        assertNull(cache.getPreview("job"))
        assertNull(cache.getParsed("job"))
    }

    private fun preview() = YoutubeTakeoutPreviewItem(
        counts = YoutubeTakeoutCategoryCounts(0, 0, 0),
        dedup = YoutubeTakeoutCategoryCounts(0, 0, 0),
        samples = YoutubeTakeoutPreviewSamples(emptyList(), emptyList(), emptyList()),
        warnings = emptyList(),
        errors = emptyList(),
    )

    private fun parsedData(value: String) = YoutubeTakeoutParsedData(
        subscriptions = emptyList(),
        playlists = emptyList(),
        playlistItems = mapOf(value to emptyList()),
        favorites = emptyList(),
        watchLater = emptyList(),
        history = emptyList(),
        warnings = emptyList(),
        errors = emptyList(),
    )
}
