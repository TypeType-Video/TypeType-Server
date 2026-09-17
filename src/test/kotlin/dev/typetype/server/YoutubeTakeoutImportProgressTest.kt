package dev.typetype.server

import dev.typetype.server.services.YoutubeTakeoutImportProgress
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class YoutubeTakeoutImportProgressTest {
    @Test
    fun `publishes increasing percentages once and reaches ninety nine`() = runBlocking {
        val progress = YoutubeTakeoutImportProgress(total = 10)
        val observed = mutableListOf<Int>()
        val draining = launch { progress.drain { observed += it } }

        repeat(10) { progress.offer(it + 1L) }
        progress.finish()
        progress.close()
        draining.join()

        assertEquals((9..99 step 10).toList(), observed)
    }
}
