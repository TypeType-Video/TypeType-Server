package dev.typetype.server

import dev.typetype.server.routes.youtubeTakeoutImportRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.FavoritesService
import dev.typetype.server.services.HistoryService
import dev.typetype.server.services.PlaylistService
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.WatchLaterService
import dev.typetype.server.services.YoutubeTakeoutImportJobService
import dev.typetype.server.services.YoutubeTakeoutImporterService
import dev.typetype.server.services.YoutubeTakeoutParserService
import dev.typetype.server.services.YoutubeTakeoutPreviewLookupService
import dev.typetype.server.services.YoutubeTakeoutPreviewService
import dev.typetype.server.services.YoutubeTakeoutSignalImportService
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
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class YoutubeTakeoutImportRoutesTest {
    private val auth = AuthService.fixed(TEST_USER_ID)
    private val subscriptions = SubscriptionsService()
    private val playlists = PlaylistService()
    private val history = HistoryService()
    private val favorites = FavoritesService()
    private val watchLater = WatchLaterService()
    private val previewLookup = YoutubeTakeoutPreviewLookupService(history, favorites, watchLater)
    private val signalImport = YoutubeTakeoutSignalImportService(favorites, watchLater, history)
    private val importService = YoutubeTakeoutImportJobService(
        YoutubeTakeoutParserService(),
        YoutubeTakeoutPreviewService(subscriptions, playlists, previewLookup),
        YoutubeTakeoutImporterService(subscriptions, playlists, signalImport),
    )

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
    }

    @Test
    fun `upload preview commit and report`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { youtubeTakeoutImportRoutes(importService, auth) }
        }
        val zip = createTakeoutZip()
        val upload = uploadArchive(zip)
        assertEquals(HttpStatusCode.Created, upload.status)
        val jobId = Json.parseToJsonElement(upload.bodyAsText()).jsonObject["jobId"]!!.jsonPrimitive.content
        val preview = client.get("/imports/youtube-takeout/$jobId/preview") { header(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.OK, preview.status)
        val commit = client.post("/imports/youtube-takeout/$jobId/commit") { header(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.Accepted, commit.status)
        var report = client.get("/imports/youtube-takeout/$jobId/report") { header(HttpHeaders.Authorization, "Bearer test-jwt") }
        repeat(25) {
            if (report.status == HttpStatusCode.OK) return@repeat
            delay(80)
            report = client.get("/imports/youtube-takeout/$jobId/report") { header(HttpHeaders.Authorization, "Bearer test-jwt") }
        }
        assertEquals(HttpStatusCode.OK, report.status)
        Files.deleteIfExists(zip)
    }

    @Test
    fun `commit returns running status while import finishes in background`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { youtubeTakeoutImportRoutes(importService, auth) }
        }
        val zip = createTakeoutZip()
        val upload = uploadArchive(zip)
        val jobId = Json.parseToJsonElement(upload.bodyAsText()).jsonObject["jobId"]!!.jsonPrimitive.content
        val preview = client.get("/imports/youtube-takeout/$jobId/preview") { header(HttpHeaders.Authorization, "Bearer test-jwt") }

        val commit = client.post("/imports/youtube-takeout/$jobId/commit") { header(HttpHeaders.Authorization, "Bearer test-jwt") }

        assertEquals(HttpStatusCode.OK, preview.status)
        assertEquals(HttpStatusCode.Accepted, commit.status)
        val body = Json.parseToJsonElement(commit.bodyAsText()).jsonObject
        assertEquals("running", body["status"]!!.jsonPrimitive.content)
        assertEventuallyCompleted(jobId)
        Files.deleteIfExists(zip)
    }

    @Test
    fun `preview failure leaves terminal failed status`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { youtubeTakeoutImportRoutes(importService, auth) }
        }
        val zip = createInvalidZip()
        val upload = uploadArchive(zip)
        val jobId = Json.parseToJsonElement(upload.bodyAsText()).jsonObject["jobId"]!!.jsonPrimitive.content

        val preview = client.get("/imports/youtube-takeout/$jobId/preview") { header(HttpHeaders.Authorization, "Bearer test-jwt") }
        val status = client.get("/imports/youtube-takeout/$jobId") { header(HttpHeaders.Authorization, "Bearer test-jwt") }
        val body = Json.parseToJsonElement(status.bodyAsText()).jsonObject

        assertEquals(HttpStatusCode.BadRequest, preview.status)
        assertEquals("failed", body["status"]!!.jsonPrimitive.content)
        assertEquals("parsing_failed", body["phase"]!!.jsonPrimitive.content)
        Files.deleteIfExists(zip)
    }

    private suspend fun ApplicationTestBuilder.assertEventuallyCompleted(jobId: String) {
        var status = client.get("/imports/youtube-takeout/$jobId") { header(HttpHeaders.Authorization, "Bearer test-jwt") }
        repeat(25) {
            if (Json.parseToJsonElement(status.bodyAsText()).jsonObject["status"]!!.jsonPrimitive.content == "completed") return
            delay(80)
            status = client.get("/imports/youtube-takeout/$jobId") { header(HttpHeaders.Authorization, "Bearer test-jwt") }
        }
        assertEquals("completed", Json.parseToJsonElement(status.bodyAsText()).jsonObject["status"]!!.jsonPrimitive.content)
    }

    private suspend fun ApplicationTestBuilder.uploadArchive(zip: Path) = client.post("/imports/youtube-takeout") {
        header(HttpHeaders.Authorization, "Bearer test-jwt")
        setBody(MultiPartFormDataContent(formData {
            append("archive", Files.readAllBytes(zip), Headers.build {
                append(HttpHeaders.ContentType, ContentType.Application.Zip.toString())
                append(HttpHeaders.ContentDisposition, "filename=takeout.zip")
            })
        }))
    }

    private fun createTakeoutZip(): Path {
        val zip = Files.createTempFile("yt-takeout-", ".zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("subscriptions.csv"))
            out.write("channel id,channel title\nUC1,Linus Tech Tips\n".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("playlists.csv"))
            out.write("playlist id,title,description\nPL1,Tech Picks,desc\n".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("playlist_items.csv"))
            out.write("playlist id,video id,video title\nPL1,abc123,Video\n".toByteArray())
            out.closeEntry()
        }
        return zip
    }

    private fun createInvalidZip(): Path {
        val zip = Files.createTempFile("yt-takeout-invalid-", ".zip")
        Files.writeString(zip, "not a zip")
        return zip
    }

}
