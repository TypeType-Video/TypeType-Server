package dev.typetype.server

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.portability.*
import dev.typetype.server.routes.portabilityRoutes
import dev.typetype.server.services.AuthService
import io.ktor.client.request.forms.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.*
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@EnabledIfEnvironmentVariable(named = "PORTABILITY_REPRO_ARCHIVE", matches = ".+")
class PortabilityArchiveReproductionTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `real archive reaches preview without account writes`() = testApplication {
        val archive = Path.of(System.getenv("PORTABILITY_REPRO_ARCHIVE"))
        val data = object : PortabilityDataPort {
            override suspend fun import(
                userId: String,
                source: PortabilityRecordSource,
                request: PortabilityImportRequest,
                onCategoryComplete: (PortabilityCategory, Long) -> Unit,
            ): Map<String, Long> = error("Account writes are forbidden in this probe")

            override suspend fun export(
                userId: String,
                categories: Set<PortabilityCategory>,
                sink: PortabilityRecordSink,
                onCategoryComplete: (PortabilityCategory, Long) -> Unit,
            ) = error("Account reads are forbidden in this probe")
        }
        val engine = PortabilityEngine(
            PortabilityRegistry(listOf(YoutubeTakeoutPortabilityAdapter())), data,
            PortabilityJobStore(directory.resolve("jobs")),
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        application {
            install(ContentNegotiation) { json(CacheJson) }
            configureStatusPages()
            routing { portabilityRoutes(engine, AuthService.fixed("owner")) }
        }
        try {
            val response = client.post("/portability/imports?format=youtube-takeout") {
                header(HttpHeaders.Authorization, "Bearer test-jwt")
                setBody(MultiPartFormDataContent(formData {
                    append("file", InputProvider(Files.size(archive)) {
                        Files.newInputStream(archive).asSource().buffered()
                    }, Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=takeout.zip")
                        append(HttpHeaders.ContentType, "application/zip")
                    })
                }))
            }
            val body = response.bodyAsText()
            if (Files.size(archive) > PortabilityLimits.MAX_UPLOAD_BYTES) {
                assertEquals(HttpStatusCode.PayloadTooLarge, response.status, body)
                return@testApplication
            }
            assertEquals(HttpStatusCode.Accepted, response.status, body)
            val id = CacheJson.decodeFromString<PortabilityJobSnapshot>(body).id
            withTimeout(120_000) {
                while (engine.snapshot("owner", id).state in setOf(
                        PortabilityJobState.QUEUED, PortabilityJobState.ANALYZING,
                    )) delay(100)
            }
            val result = engine.snapshot("owner", id)
            assertEquals(PortabilityJobState.READY, result.state, result.errorMessage)
            println("Archive preview counts: ${result.preview?.counts}")
        } finally {
            engine.close()
        }
    }
}
