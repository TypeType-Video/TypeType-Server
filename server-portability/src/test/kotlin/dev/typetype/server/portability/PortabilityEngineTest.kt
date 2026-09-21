package dev.typetype.server.portability

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

class PortabilityEngineTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `preview is isolated by owner and can be applied`() = runBlocking {
        val dataPort = FakeDataPort()
        val engine = engine(dataPort)
        val upload = directory.resolve("input.json")
        Files.writeString(upload, "fixture")

        val started = engine.startImportPreview("owner-a", upload, "input.json", "application/json")
        val ready = awaitState(engine, "owner-a", started.id, PortabilityJobState.READY)

        assertEquals(PortabilityFormat.NEW_PIPE, ready.preview?.detection?.format)
        assertEquals(1L, ready.preview?.counts?.get("subscriptions"))
        assertThrows(PortabilityJobNotFoundException::class.java) {
            engine.snapshot("owner-b", started.id)
        }

        engine.applyImport(
            "owner-a",
            started.id,
            PortabilityImportRequest(setOf(PortabilityCategory.SUBSCRIPTIONS)),
        )
        val completed = awaitState(engine, "owner-a", started.id, PortabilityJobState.COMPLETED)
        assertEquals(1L, completed.result?.get("subscriptions"))
        assertEquals(PortabilityProgressUnit.RECORDS, completed.progress?.unit)
        assertEquals(1L, completed.progress?.processed)
        assertEquals(1L, completed.progress?.total)
        engine.close()
    }

    @Test
    fun `export produces an owned artifact`() = runBlocking {
        val engine = engine(FakeDataPort())
        val started = engine.startExport("owner", PortabilityFormat.NEW_PIPE, setOf(PortabilityCategory.SUBSCRIPTIONS))
        val completed = awaitState(engine, "owner", started.id, PortabilityJobState.COMPLETED)

        assertNotNull(completed.preview)
        assertEquals("exported", Files.readString(engine.artifact("owner", started.id)))
        engine.close()
    }

    @Test
    fun `failed job keeps safe diagnostics for support`() = runBlocking {
        val engine = engine(FakeDataPort(), FailingAdapter())
        val upload = directory.resolve("invalid.json")
        Files.writeString(upload, "fixture")

        val started = engine.startImportPreview(
            "owner",
            upload,
            "invalid.json",
            "application/json",
            requestId = "request-portability-test",
        )
        val failed = awaitState(engine, "owner", started.id, PortabilityJobState.FAILED)

        assertEquals("request-portability-test", failed.requestId)
        assertEquals("portability_invalid_input", failed.errorCode)
        assertEquals("Unsupported backup version", failed.errorMessage)
        assertEquals(failed.requestId, engine.report("owner", started.id).requestId)
        engine.close()
    }

    @Test
    fun `cancelled analysis stops before its files can be deleted`() = runBlocking {
        val engine = engine(FakeDataPort(), SlowAdapter())
        val upload = directory.resolve("slow.json")
        Files.writeString(upload, "fixture")
        val started = engine.startImportPreview("owner", upload, "slow.json", "application/json")
        var progressed = false
        repeat(100) {
            if (!progressed) {
                progressed = (engine.snapshot("owner", started.id).progress?.processed ?: 0L) > 0L
                if (!progressed) delay(5)
            }
        }
        assertEquals(true, progressed)

        engine.cancel("owner", started.id)
        repeat(100) {
            if (runCatching { engine.delete("owner", started.id) }.isSuccess) {
                assertThrows(PortabilityJobNotFoundException::class.java) { engine.snapshot("owner", started.id) }
                engine.close()
                return@runBlocking
            }
            delay(5)
        }
        error("Cancelled portability analysis did not stop")
    }

    private fun engine(dataPort: PortabilityDataPort, adapter: PortabilityAdapter = FakeAdapter()): PortabilityEngine {
        val store = PortabilityJobStore(directory.resolve("jobs"))
        return PortabilityEngine(
            PortabilityRegistry(listOf(adapter)),
            dataPort,
            store,
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }

    private suspend fun awaitState(
        engine: PortabilityEngine,
        owner: String,
        id: String,
        expected: PortabilityJobState,
    ): PortabilityJobSnapshot {
        repeat(100) {
            val snapshot = engine.snapshot(owner, id)
            if (snapshot.state == expected) return snapshot
            if (snapshot.state == PortabilityJobState.FAILED) error("Portability job failed")
            delay(10)
        }
        error("Portability job did not reach $expected")
    }
}

private class SlowAdapter : PortabilityAdapter by FakeAdapter() {
    override fun decode(input: PortabilityInput, sink: PortabilityRecordSink) {
        repeat(10_000) { index ->
            Thread.sleep(1)
            sink.write(PortabilitySubscription("https://youtube.com/channel/UC$index"))
        }
    }
}

private class FailingAdapter : PortabilityAdapter by FakeAdapter() {
    override fun decode(input: PortabilityInput, sink: PortabilityRecordSink) {
        throw IllegalArgumentException("Unsupported backup version")
    }
}

private open class FakeAdapter : PortabilityAdapter {
    override val descriptor = PortabilityAdapterDescriptor(
        PortabilityFormat.NEW_PIPE,
        1,
        setOf(
            PortabilityCapability(
                PortabilityCategory.SUBSCRIPTIONS,
                setOf(PortabilityDirection.IMPORT, PortabilityDirection.EXPORT),
                PortabilityFidelity.COMPLETE,
            ),
        ),
        "json",
        "application/json",
    )

    override fun detect(input: PortabilityInput) = PortabilityDetection(
        PortabilityFormat.NEW_PIPE,
        "1",
        100,
        "test fixture",
    )

    override fun decode(input: PortabilityInput, sink: PortabilityRecordSink) {
        sink.markCategory(PortabilityCategory.SUBSCRIPTIONS)
        sink.write(PortabilitySubscription("https://youtube.com/channel/UC1"))
    }

    override fun encode(
        source: PortabilityRecordSource,
        output: OutputStream,
        categories: Set<PortabilityCategory>,
    ) {
        output.write("exported".toByteArray())
    }
}

private class FakeDataPort : PortabilityDataPort {
    override suspend fun import(
        userId: String,
        source: PortabilityRecordSource,
        request: PortabilityImportRequest,
        onCategoryComplete: (PortabilityCategory, Long) -> Unit,
        onCategoryProgress: (PortabilityCategory, Long) -> Unit,
    ): Map<String, Long> = source.counts().mapKeys { it.key.wireName }.also { result ->
        request.categories.forEach { category ->
            val count = result[category.wireName] ?: 0L
            onCategoryProgress(category, count)
            onCategoryComplete(category, count)
        }
    }

    override suspend fun export(
        userId: String,
        categories: Set<PortabilityCategory>,
        sink: PortabilityRecordSink,
        onCategoryComplete: (PortabilityCategory, Long) -> Unit,
    ) {
        sink.markCategory(PortabilityCategory.SUBSCRIPTIONS)
        sink.write(PortabilitySubscription("https://youtube.com/channel/UC1"))
        onCategoryComplete(PortabilityCategory.SUBSCRIPTIONS, 1L)
    }
}
