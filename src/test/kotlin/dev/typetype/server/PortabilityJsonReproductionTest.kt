package dev.typetype.server

import dev.typetype.server.portability.PortabilityCategory
import dev.typetype.server.portability.PortabilityInputFactory
import dev.typetype.server.portability.PortabilityLimits
import dev.typetype.server.portability.PortabilitySpool
import dev.typetype.server.portability.YoutubeTakeoutPortabilityAdapter
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.nio.file.Files
import java.nio.file.Path

@EnabledIfEnvironmentVariable(named = "PORTABILITY_REPRO_JSON", matches = ".+")
class PortabilityJsonReproductionTest {
    @Test
    fun `public My Activity JSON reaches preview without account writes`() {
        val path = Path.of(System.getenv("PORTABILITY_REPRO_JSON"))
        require(Files.size(path) <= PortabilityLimits.MAX_UPLOAD_BYTES)
        val input = PortabilityInputFactory.create(path, "watch-history.json", "application/json")
        val spool = PortabilitySpool.create(path.parent ?: Path.of("/tmp"))
        try {
            YoutubeTakeoutPortabilityAdapter().decode(input, spool)
            val counts = spool.counts()
            assertTrue((counts[PortabilityCategory.HISTORY] ?: 0L) > 0L, counts.toString())
            println("JSON preview counts: ${counts.filterKeys { it in setOf(PortabilityCategory.HISTORY, PortabilityCategory.FAVORITES, PortabilityCategory.SUBSCRIPTIONS) }}")
        } finally {
            spool.delete()
        }
    }
}
