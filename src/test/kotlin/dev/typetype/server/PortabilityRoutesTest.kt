package dev.typetype.server

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.portability.NewPipePortabilityAdapter
import dev.typetype.server.portability.PortabilityCategory
import dev.typetype.server.portability.PortabilityDataPort
import dev.typetype.server.portability.PortabilityEngine
import dev.typetype.server.portability.PortabilityExportRequest
import dev.typetype.server.portability.PortabilityFormat
import dev.typetype.server.portability.PortabilityImportRequest
import dev.typetype.server.portability.PortabilityJobSnapshot
import dev.typetype.server.portability.PortabilityJobState
import dev.typetype.server.portability.PortabilityJobStore
import dev.typetype.server.portability.PortabilityRecordSink
import dev.typetype.server.portability.PortabilityRecordSource
import dev.typetype.server.portability.PortabilityRegistry
import dev.typetype.server.portability.PortabilitySubscription
import dev.typetype.server.routes.portabilityRoutes
import dev.typetype.server.services.AuthService
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PortabilityRoutesTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `authenticated account can preview apply export and delete`() = testApplication {
        val engine = engine()
        application {
            installRequestObservability()
            install(ContentNegotiation) { json(CacheJson) }
            routing { portabilityRoutes(engine, AuthService.fixed("owner")) }
        }

        val formats = client.get("/portability/formats") { authorize() }
        assertEquals(HttpStatusCode.OK, formats.status)

        val upload = client.post("/portability/imports") {
            authorize()
            header(REQUEST_ID_HEADER, "portability-route-request")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            """{"subscriptions":[],"app_version":"0.28.0"}""".toByteArray(),
                            Headers.build {
                                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                append(HttpHeaders.ContentDisposition, "filename=newpipe.json")
                            },
                        )
                    },
                ),
            )
        }
        assertEquals(HttpStatusCode.Accepted, upload.status)
        val uploaded = upload.snapshot()
        assertEquals("portability-route-request", uploaded.requestId)
        val importId = uploaded.id
        val preview = awaitState(importId, PortabilityJobState.READY)
        assertEquals(0L, preview.preview?.counts?.get("subscriptions"))
        assertEquals(0L, preview.progress?.processed)

        val apply = client.post("/portability/jobs/$importId/apply") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(CacheJson.encodeToString(PortabilityImportRequest.serializer(), PortabilityImportRequest(setOf(PortabilityCategory.SUBSCRIPTIONS))))
        }
        assertEquals(HttpStatusCode.Accepted, apply.status)
        awaitState(importId, PortabilityJobState.COMPLETED)

        val export = client.post("/portability/exports") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(
                CacheJson.encodeToString(
                    PortabilityExportRequest.serializer(),
                    PortabilityExportRequest(PortabilityFormat.NEW_PIPE, setOf(PortabilityCategory.SUBSCRIPTIONS)),
                ),
            )
        }
        assertEquals(HttpStatusCode.Accepted, export.status)
        val exportId = export.snapshot().id
        awaitState(exportId, PortabilityJobState.COMPLETED)
        val report = client.get("/portability/jobs/$exportId/report") { authorize() }
        assertEquals(HttpStatusCode.OK, report.status)
        val artifact = client.get("/portability/jobs/$exportId/artifact") { authorize() }
        assertEquals(HttpStatusCode.OK, artifact.status)
        assertEquals(listOf(0x50, 0x4b), artifact.body<ByteArray>().take(2).map(Byte::toInt))

        val deleted = client.delete("/portability/jobs/$importId") { authorize() }
        assertEquals(HttpStatusCode.NoContent, deleted.status)
        engine.close()
    }

    private fun engine() = PortabilityEngine(
        PortabilityRegistry(listOf(NewPipePortabilityAdapter())),
        RouteDataPort,
        PortabilityJobStore(directory.resolve("jobs")),
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    private fun io.ktor.client.request.HttpRequestBuilder.authorize() =
        header(HttpHeaders.Authorization, "Bearer test-jwt")

    private suspend fun io.ktor.client.statement.HttpResponse.snapshot(): PortabilityJobSnapshot =
        CacheJson.decodeFromString(PortabilityJobSnapshot.serializer(), bodyAsText())

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.awaitState(
        id: String,
        expected: PortabilityJobState,
    ): PortabilityJobSnapshot {
        repeat(100) {
            val response = client.get("/portability/jobs/$id") { authorize() }
            val snapshot = CacheJson.decodeFromString(PortabilityJobSnapshot.serializer(), response.bodyAsText())
            if (snapshot.state == expected) return snapshot
            delay(10)
        }
        error("Job did not reach $expected")
    }
}

private object RouteDataPort : PortabilityDataPort {
    override suspend fun import(
        userId: String,
        source: PortabilityRecordSource,
        request: PortabilityImportRequest,
        onCategoryComplete: (PortabilityCategory, Long) -> Unit,
        onCategoryProgress: (PortabilityCategory, Long) -> Unit,
    ) = source.counts().mapKeys { it.key.wireName }.also { result ->
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
        sink.write(PortabilitySubscription("https://youtube.com/channel/UC1", "One"))
        onCategoryComplete(PortabilityCategory.SUBSCRIPTIONS, 1L)
    }
}
