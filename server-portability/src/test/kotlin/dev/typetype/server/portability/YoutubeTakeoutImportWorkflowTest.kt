package dev.typetype.server.portability

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class YoutubeTakeoutImportWorkflowTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `imports subscriptions then recent history and playlists in committed batches`() = runBlocking {
        val source = PortabilitySpool.create(directory)
        try {
            repeat(125) { source.write(PortabilitySubscription("https://youtube.com/channel/UC$it")) }
            repeat(1_025) { index -> source.write(history(index)) }
            source.write(PortabilityPlaylist("favorites", "Favorites"))
            repeat(55) { index ->
                source.write(
                    PortabilityPlaylistVideo(
                        "favorites",
                        index,
                        video(index),
                    ),
                )
            }

            val dataPort = RecordingDataPort()
            val job = PortabilityJob(
                id = "takeout-workflow",
                ownerId = "owner",
                kind = PortabilityJobKind.IMPORT,
                directory = directory,
                requestId = null,
                clock = System::currentTimeMillis,
            )
            val progress = PortabilityProgressReporter(
                job,
                PortabilityProgressPhase.APPLYING,
                PortabilityProgressUnit.RECORDS,
                total = 1_206L,
                interval = 100L,
            )
            var partial = emptyMap<String, Long>()

            val result = YoutubeTakeoutImportWorkflow(dataPort).apply(
                "owner",
                source,
                PortabilityImportRequest(
                    setOf(PortabilityCategory.SUBSCRIPTIONS, PortabilityCategory.HISTORY, PortabilityCategory.PLAYLISTS),
                    PortabilityDuplicatePolicy.REPLACE,
                ),
                progress,
            ) {
                partial = it
                job.updateResult(it)
            }

            assertEquals(
                listOf(
                    PortabilityCategory.SUBSCRIPTIONS to 50,
                    PortabilityCategory.SUBSCRIPTIONS to 50,
                    PortabilityCategory.SUBSCRIPTIONS to 25,
                    PortabilityCategory.HISTORY to 20,
                    PortabilityCategory.PLAYLISTS to 1,
                    PortabilityCategory.PLAYLISTS to 50,
                    PortabilityCategory.PLAYLISTS to 5,
                    PortabilityCategory.HISTORY to 500,
                    PortabilityCategory.HISTORY to 500,
                    PortabilityCategory.HISTORY to 5,
                ),
                dataPort.batches.map { it.category to it.records.size },
            )
            assertEquals((1_024 downTo 1_005).map(Int::toLong), dataPort.batches[3].records.map { (it as PortabilityHistory).watchedAt })
            assertEquals((0 until 500).map(Int::toLong), dataPort.batches[7].records.map { (it as PortabilityHistory).watchedAt })
            assertEquals(125L, result["subscriptions"])
            assertEquals(1_025L, result["history"])
            assertEquals(56L, result["playlists"])
            assertEquals(result, partial)
            assertEquals(result, job.snapshot().result)
            assertEquals(1_206L, job.snapshot().progress?.processed)
            assertEquals(1_206L, job.snapshot().progress?.total)
            assertEquals(1_005L, job.snapshot().progress?.stageProcessed)
            assertEquals(1_005L, job.snapshot().progress?.stageTotal)
            assertEquals(10L, job.snapshot().progress?.checkpoint)
            assertEquals(PortabilityDuplicatePolicy.REPLACE, dataPort.batches.first().policy)
            assertEquals(PortabilityDuplicatePolicy.SKIP, dataPort.batches[1].policy)
            assertEquals(PortabilityDuplicatePolicy.REPLACE, dataPort.batches[3].policy)
            assertEquals(PortabilityDuplicatePolicy.REPLACE, dataPort.batches[4].policy)
            assertEquals(PortabilityDuplicatePolicy.SKIP, dataPort.batches[5].policy)
            assertEquals(PortabilityDuplicatePolicy.SKIP, dataPort.batches[7].policy)
        } finally {
            source.delete()
        }
    }

    private fun history(index: Int) = PortabilityHistory(video(index), index.toLong())

    private fun video(index: Int) = PortabilityVideo(
        url = "https://youtube.com/watch?v=$index",
        title = "Video $index",
        thumbnailUrl = "",
        durationSeconds = 60L,
        channelName = "Channel",
        channelUrl = "https://youtube.com/channel/channel",
    )
}

private class RecordingDataPort : PortabilityDataPort {
    val batches = mutableListOf<Batch>()

    override suspend fun import(
        userId: String,
        source: PortabilityRecordSource,
        request: PortabilityImportRequest,
        onCategoryComplete: (PortabilityCategory, Long) -> Unit,
        onCategoryProgress: (PortabilityCategory, Long) -> Unit,
    ): Map<String, Long> {
        val category = request.categories.single()
        val records = buildList { source.forEach(category, ::add) }
        batches += Batch(category, records, request.duplicatePolicy)
        onCategoryProgress(category, records.size.toLong())
        onCategoryComplete(category, records.size.toLong())
        return mapOf(category.wireName to records.size.toLong())
    }

    override suspend fun export(
        userId: String,
        categories: Set<PortabilityCategory>,
        sink: PortabilityRecordSink,
        onCategoryComplete: (PortabilityCategory, Long) -> Unit,
    ) = Unit

    data class Batch(
        val category: PortabilityCategory,
        val records: List<PortabilityRecord>,
        val policy: PortabilityDuplicatePolicy,
    )
}
