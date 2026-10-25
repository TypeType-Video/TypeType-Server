package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.CacheAdmission
import dev.typetype.server.services.PublicCacheArea
import dev.typetype.server.services.PublicExtractionCache
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PublicExtractionCacheAdmissionTest {
    private var now = 1_000_000L
    private val cache = ExpiringFakeCache { now }
    private var loads = 0
    private val area = PublicCacheArea("test", ListSerializer(String.serializer())) { items ->
        when {
            items.isEmpty() -> CacheAdmission.Suspect()
            items == listOf("bot") -> CacheAdmission.Reject("bot page")
            else -> CacheAdmission.Valid(600L)
        }
    }

    private suspend fun get(result: ExtractionResult<List<String>>): ExtractionResult<List<String>> =
        PublicExtractionCache.getOrLoad(cache, area, KEY, clock = { now }) {
            loads += 1
            result
        }

    @Test
    fun `valid results are cached and kept as the last good value`() = runTest {
        assertEquals(ExtractionResult.Success(listOf("a")), get(ExtractionResult.Success(listOf("a"))))

        assertEquals(600L, cache.ttls[KEY])
        assertEquals(86_400L, cache.ttls["$KEY:good"])
    }

    @Test
    fun `empty results without a last good value are kept for thirty seconds only`() = runTest {
        assertEquals(ExtractionResult.Success(emptyList<String>()), get(ExtractionResult.Success(emptyList())))

        assertEquals(30L, cache.ttls[KEY])
        assertTrue("$KEY:good" !in cache.keys())
    }

    @Test
    fun `suspect results fall back to the last good value and back off`() = runTest {
        get(ExtractionResult.Success(listOf("a")))
        now += 601_000L

        assertEquals(ExtractionResult.Success(listOf("a")), get(ExtractionResult.Success(emptyList())))
        assertEquals(ExtractionResult.Success(listOf("a")), get(ExtractionResult.Success(emptyList())))

        assertEquals(2, loads)
    }

    @Test
    fun `failures serve the last good value with exponential backoff`() = runTest {
        get(ExtractionResult.Success(listOf("a")))
        now += 601_000L
        val failure = ExtractionResult.Failure("blocked")

        assertEquals(ExtractionResult.Success(listOf("a")), get(failure))
        now += 29_000L
        get(failure)
        assertEquals(2, loads)
        now += 2_000L
        get(failure)
        assertEquals(3, loads)
        now += 59_000L
        get(failure)
        assertEquals(3, loads)
        now += 2_000L
        get(failure)
        assertEquals(4, loads)
    }

    @Test
    fun `failures without a last good value are replayed during the backoff`() = runTest {
        val failure = ExtractionResult.Failure("blocked", "provider_blocked")

        assertEquals(failure, get(failure))
        assertEquals(failure, get(ExtractionResult.Success(listOf("never"))))

        assertEquals(1, loads)
    }

    @Test
    fun `rejected results become failures`() = runTest {
        val result = get(ExtractionResult.Success(listOf("bot")))

        assertEquals(ExtractionResult.Failure("bot page", PublicExtractionCache.REJECTED_CODE), result)
        assertTrue(KEY !in cache.keys())
    }

    @Test
    fun `a valid result after failures clears the backoff`() = runTest {
        get(ExtractionResult.Failure("blocked"))
        now += 31_000L
        get(ExtractionResult.Success(listOf("b")))
        now += 601_000L

        assertEquals(ExtractionResult.Success(listOf("c")), get(ExtractionResult.Success(listOf("c"))))
        assertTrue("$KEY:backoff" !in cache.keys())
    }

    @Test
    fun `ttl based areas keep their previous behaviour`() = runTest {
        PublicExtractionCache.getOrLoad(cache, "legacy", "legacy:key", String.serializer(), { 60L }) {
            ExtractionResult.Failure("down")
        }
        PublicExtractionCache.getOrLoad(cache, "legacy", "legacy:key", String.serializer(), { 60L }) {
            ExtractionResult.Success("")
        }

        assertEquals(setOf("legacy:key"), cache.keys())
    }

    private companion object {
        const val KEY = "test:v2:key"
    }
}
