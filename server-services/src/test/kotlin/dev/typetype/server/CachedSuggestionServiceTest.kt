package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.CachedSuggestionService
import dev.typetype.server.services.SuggestionService
import dev.typetype.server.services.YOUTUBE_SERVICE_ID
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CachedSuggestionServiceTest {

    private val delegate: SuggestionService = mockk()
    private val cache: CacheService = mockk()
    private val service = CachedSuggestionService(delegate, cache)

    @Test
    fun `cache hit returns cached suggestions without calling delegate`() = runBlocking {
        coEvery { cache.get(any()) } returns """["rick astley","rickroll"]"""
        val result = service.getSuggestions("rick", YOUTUBE_SERVICE_ID)
        assertEquals(ExtractionResult.Success(listOf("rick astley", "rickroll")), result)
        coVerify(exactly = 1) { cache.get(match { it.startsWith("suggestions:v2:") && !it.contains("rick") }) }
        coVerify(exactly = 0) { delegate.getSuggestions(any(), any()) }
    }

    @Test
    fun `cache miss delegates and stores result`() = runBlocking {
        coEvery { cache.get(any()) } returns null
        coEvery { delegate.getSuggestions("rick", YOUTUBE_SERVICE_ID) } returns ExtractionResult.Success(listOf("rick astley"))
        coEvery { cache.set(any(), any(), any()) } returns Unit
        val result = service.getSuggestions("rick", YOUTUBE_SERVICE_ID)
        assertEquals(ExtractionResult.Success(listOf("rick astley")), result)
        coVerify(exactly = 1) {
            cache.set(match { it.startsWith("suggestions:v2:") && !it.contains("rick") }, any(), 1800L)
        }
    }

    @Test
    fun `corrupt cache delegates and stores refreshed result`() = runBlocking {
        coEvery { cache.get(any()) } returns "not-json"
        coEvery { delegate.getSuggestions("rick", YOUTUBE_SERVICE_ID) } returns ExtractionResult.Success(listOf("rick astley"))
        coEvery { cache.set(any(), any(), any()) } returns Unit
        val result = service.getSuggestions("rick", YOUTUBE_SERVICE_ID)
        assertEquals(ExtractionResult.Success(listOf("rick astley")), result)
        coVerify(exactly = 1) { delegate.getSuggestions("rick", YOUTUBE_SERVICE_ID) }
        coVerify(exactly = 1) { cache.set(any(), any(), 1800L) }
    }

    @Test
    fun `delegate failure is not cached`() = runBlocking {
        coEvery { cache.get(any()) } returns null
        coEvery { delegate.getSuggestions("bad", YOUTUBE_SERVICE_ID) } returns ExtractionResult.Failure("network error")
        val result = service.getSuggestions("bad", YOUTUBE_SERVICE_ID)
        assertEquals(ExtractionResult.Failure("network error"), result)
        coVerify(exactly = 0) { cache.set(any(), any(), any()) }
    }
}
