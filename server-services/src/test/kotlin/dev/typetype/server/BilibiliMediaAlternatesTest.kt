package dev.typetype.server

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.services.ProviderMediaHandleService
import dev.typetype.server.services.decodeCachedStream
import dev.typetype.server.services.encodeCachedStream
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BilibiliMediaAlternatesTest {
    @Test
    fun cachedAlternatesStayPrivateAndSurviveCacheRoundTrip() {
        val primary = "https://upos-hz-mirrorakam.akamaized.net/video.m4s?deadline=9999999999"
        val alternate = "https://upos-sz-mirrorcosov.bilivideo.com/video.m4s?deadline=9999999999"
        val stream = testStreamResponse(
            videoOnlyStreams = listOf(testVideoStream(primary)),
        ).copy(providerMediaAlternates = mapOf(primary to listOf(alternate)))

        val cached = encodeCachedStream(stream)
        val publicJson = CacheJson.encodeToString(stream)

        assertEquals(stream.providerMediaAlternates, decodeCachedStream(cached).providerMediaAlternates)
        assertTrue(cached.contains(alternate))
        assertFalse(publicJson.contains(alternate))
    }

    @Test
    fun opaqueHandleKeepsOnlyValidatedSamePathAlternates() = runTest {
        val service = ProviderMediaHandleService(FakeCacheService(), nowSeconds = { 1_000L })
        val primary = "https://upos-hz-mirrorakam.akamaized.net/video.m4s?deadline=9999999999"
        val alternate = "https://upos-sz-mirrorcosov.bilivideo.com/video.m4s?deadline=9999999999"
        val otherPath = "https://upos-sz-mirrorcosov.bilivideo.com/other.m4s?deadline=9999999999"
        val path = service.createPath(primary, alternateUrls = listOf(alternate, otherPath))
        val target = service.resolve(path.substringAfterLast('/'))

        val alternates = target?.alternateUrls.orEmpty()
        assertEquals(alternate, alternates.first())
        assertFalse(otherPath in alternates)
        assertTrue(alternates.all { it.substringBefore('?').endsWith("/video.m4s") })
        assertFalse(path.contains("akamaized"))
        assertFalse(path.contains("bilivideo"))
    }
}
