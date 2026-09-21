package dev.typetype.server

import dev.typetype.server.services.DeArrowRemote
import dev.typetype.server.services.DeArrowService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DeArrowServiceTest {
    @Test
    fun `selects accepted title and thumbnail and caches branding`() = runBlocking {
        val remote = FakeDeArrowRemote()
        val service = DeArrowService(FakeCacheService(), remote)
        val first = service.get("stZ3ZoR_8eg")
        val second = service.get("stZ3ZoR_8eg")
        assertEquals("Clear title", first?.title)
        assertEquals("/dearrow/thumbnail?videoId=stZ3ZoR_8eg&time=12.5", first?.thumbnailUrl)
        assertEquals(listOf("Rejected title", "Clear title", "Original title"), first?.titles?.map { it.title })
        assertEquals(listOf(-2, 2, 0), first?.titles?.map { it.votes })
        assertEquals(listOf(8.0, 12.5, null), first?.thumbnails?.map { it.timestamp })
        assertEquals(0.4, first?.randomTime)
        assertEquals(100.0, first?.videoDuration)
        assertEquals(first, second)
        assertEquals(1, remote.brandingCalls)
    }

    @Test
    fun `rejects invalid video id without remote call`() = runBlocking {
        val remote = FakeDeArrowRemote()
        val service = DeArrowService(FakeCacheService(), remote)
        assertNull(service.get("invalid"))
        assertEquals(0, remote.brandingCalls)
    }
}

private class FakeDeArrowRemote : DeArrowRemote {
    var brandingCalls = 0

    override suspend fun branding(videoId: String): String {
        brandingCalls += 1
        return BRANDING
    }

    override suspend fun thumbnail(videoId: String, timestamp: Double): ByteArray = byteArrayOf(1, 2, 3)

    companion object {
        private const val BRANDING = """{"titles":[{"title":"Rejected title","votes":-2,"locked":false,"original":false,"UUID":"rejected"},{"title":"Clear title","votes":2,"locked":false,"original":false,"UUID":"accepted"},{"title":"Original title","votes":0,"locked":false,"original":true,"UUID":"original"}],"thumbnails":[{"timestamp":8.0,"votes":-1,"locked":false,"original":false,"UUID":"rejected-thumb"},{"timestamp":12.5,"votes":1,"locked":false,"original":false,"UUID":"accepted-thumb"},{"votes":0,"locked":false,"original":true,"UUID":"original-thumb"}],"videoDuration":100,"randomTime":0.4}"""
    }
}
