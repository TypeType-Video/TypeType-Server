package dev.typetype.server

import dev.typetype.server.models.ProgressItem
import dev.typetype.server.routes.progressRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.ProgressService
import io.ktor.client.request.headers
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProgressBatchWriteRoutesTest {

    private val service = ProgressService()
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() { TestDatabase.setup() }
    }

    @BeforeEach
    fun clean() { TestDatabase.truncateAll() }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { progressRoutes(service, auth) }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.putBatch(body: String, signedIn: Boolean = true): HttpResponse =
        client.put("/progress/batch") {
            if (signedIn) headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(body)
        }

    @Test
    fun `PUT progress batch writes every position in one request`() = withApp {
        service.upsert(TEST_USER_ID, "https://yt.com/watch?v=old", 5_000L)
        val items = (0 until 150).joinToString(",") { """{"videoUrl":"https://yt.com/watch?v=$it","position":${it * 1_000}}""" }
        val response = putBatch("""{"items":[$items,{"videoUrl":"https://yt.com/watch?v=old","position":0}]}""")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(151, Json.decodeFromString<List<ProgressItem>>(response.bodyAsText()).size)
        val stored = runBlocking {
            service.getMany(TEST_USER_ID, listOf("https://yt.com/watch?v=149", "https://yt.com/watch?v=old"))
        }
        assertEquals(listOf(149_000L, 0L), stored.map(ProgressItem::position))
    }

    @Test
    fun `PUT progress batch keeps the last position of a repeated video`() = withApp {
        val response = putBatch(
            """{"items":[{"videoUrl":"https://yt.com/watch?v=a","position":1000},{"videoUrl":"https://yt.com/watch?v=a","position":-5}]}""",
        )

        assertEquals(HttpStatusCode.OK, response.status)
        val stored = runBlocking { service.get(TEST_USER_ID, "https://yt.com/watch?v=a") }
        assertEquals(0L, stored?.position)
    }

    @Test
    fun `PUT progress batch validates authentication and limits`() = withApp {
        val one = """{"items":[{"videoUrl":"https://yt.com/watch?v=a","position":1}]}"""
        assertEquals(HttpStatusCode.Unauthorized, putBatch(one, signedIn = false).status)
        assertEquals(HttpStatusCode.BadRequest, putBatch("""{"items":[]}""").status)
        assertEquals(HttpStatusCode.BadRequest, putBatch("""{"items":[{"videoUrl":" ","position":1}]}""").status)
        val tooMany = (0..200).joinToString(",") { """{"videoUrl":"https://yt.com/watch?v=$it","position":1}""" }
        assertEquals(HttpStatusCode.BadRequest, putBatch("""{"items":[$tooMany]}""").status)
    }
}
