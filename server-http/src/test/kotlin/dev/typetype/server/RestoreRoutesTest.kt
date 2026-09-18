package dev.typetype.server

import dev.typetype.server.db.tables.HistoryTable
import dev.typetype.server.db.tables.PlaylistVideosTable
import dev.typetype.server.db.tables.PlaylistsTable
import dev.typetype.server.db.tables.ProgressTable
import dev.typetype.server.db.tables.SearchHistoryTable
import dev.typetype.server.db.tables.SubscriptionsTable
import dev.typetype.server.routes.restoreRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PipePipeBackupImporterService
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
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
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

class RestoreRoutesTest {

    private val auth = AuthService.fixed(TEST_USER_ID)
    private val importer = PipePipeBackupImporterService()

    companion object { @BeforeAll @JvmStatic fun initDb() = TestDatabase.setup() }

    @BeforeEach
    fun clean() { TestDatabase.truncateAll() }

    @Test
    fun `POST restore imports backup snapshot`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { restoreRoutes(importer, auth) }
        }
        val zip = PipePipeBackupTestFixtures.createBackupZip()
        val payload = Files.readAllBytes(zip)
        val response = client.post("/restore/pipepipe") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", payload, Headers.build {
                            append(HttpHeaders.ContentType, ContentType.Application.Zip.toString())
                            append(HttpHeaders.ContentDisposition, "filename=pipepipe.zip")
                        })
                    },
                ),
            )
        }
        Files.deleteIfExists(zip)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertEquals(1, countRows("history"))
        assertEquals(1, countRows("subscriptions"))
        assertEquals(1, countRows("playlists"))
        assertEquals(1, countRows("playlist_videos"))
        assertEquals(1, countRows("progress"))
        assertEquals(1, countRows("search_history"))
        val payloadJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("raw", payloadJson["timeMode"]?.jsonPrimitive?.content)
    }

    @Test
    fun `POST restore with normalized mode returns mode and bounds`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { restoreRoutes(importer, auth) }
        }
        val zip = PipePipeBackupTestFixtures.createBackupZip()
        val payload = Files.readAllBytes(zip)
        val response = client.post("/restore/pipepipe?timeMode=normalized") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            setBody(MultiPartFormDataContent(formData {
                append("file", payload, Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Zip.toString())
                    append(HttpHeaders.ContentDisposition, "filename=pipepipe.zip")
                })
            }))
        }
        Files.deleteIfExists(zip)
        val payloadJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("normalized", payloadJson["timeMode"]?.jsonPrimitive?.content)
        val min = payloadJson["historyMinWatchedAt"]?.jsonPrimitive?.content?.toLongOrNull()
        val max = payloadJson["historyMaxWatchedAt"]?.jsonPrimitive?.content?.toLongOrNull()
        assertEquals(true, min != null)
        assertEquals(true, max != null)
    }

    private fun countRows(table: String): Long = transaction {
        when (table) {
            "history" -> HistoryTable.selectAll().count()
            "subscriptions" -> SubscriptionsTable.selectAll().count()
            "playlists" -> PlaylistsTable.selectAll().count()
            "playlist_videos" -> PlaylistVideosTable.selectAll().count()
            "progress" -> ProgressTable.selectAll().count()
            "search_history" -> SearchHistoryTable.selectAll().count()
            else -> 0
        }
    }
}
