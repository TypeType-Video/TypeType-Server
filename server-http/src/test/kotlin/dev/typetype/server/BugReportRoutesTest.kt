package dev.typetype.server

import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.routes.bugReportRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.BugReportService
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BugReportRoutesTest {
    private val auth = AuthService.fixed(TEST_USER_ID)
    private val service = BugReportService()

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        transaction {
            UsersTable.insert {
                it[id] = TEST_USER_ID
                it[email] = "reporter@test.local"
                it[passwordHash] = "hash"
                it[name] = "Reporter"
                it[role] = "user"
                it[createdAt] = 1L
                it[updatedAt] = 1L
            }
        }
    }

    @Test
    fun `POST bug report creates report`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { bugReportRoutes(service, auth) }
        }
        val response = client.post("/bug-reports") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
            contentType(ContentType.Application.Json)
            setBody("""{"category":"player","description":"Video freezes","context":{"route":"/watch","timestamp":1774200000000,"userAgent":"Mozilla","browserLanguage":"fr-FR"}}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("\"status\":\"new\""))
    }

    @Test
    fun `POST bug report accepts api errors in context`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { bugReportRoutes(service, auth) }
        }
        val response = client.post("/bug-reports") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
            contentType(ContentType.Application.Json)
            setBody(
                """{"category":"ui","description":"Issue report","context":{"route":"/watch","timestamp":1774200000000,"userAgent":"Mozilla","browserLanguage":"fr-FR","apiErrors":[{"requestId":"req-123","endpoint":"/streams","status":400,"code":"BAD_REQUEST","message":"Invalid url","timestamp":1774200000001}]}}""",
            )
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `POST bug report accepts bounded device diagnostics`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { bugReportRoutes(service, auth) }
        }
        val response = client.post("/bug-reports") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
            contentType(ContentType.Application.Json)
            setBody(
                """{"category":"ui","description":"Responsive issue","context":{"route":"/settings","timestamp":1774200000000,"userAgent":"Mozilla","browserLanguage":"fr-FR","viewportWidth":390,"viewportHeight":844,"screenWidth":430,"screenHeight":932,"devicePixelRatio":3.0,"online":true,"timezone":"Europe/Paris"}}""",
            )
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `POST bug report with invalid category returns 400`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { bugReportRoutes(service, auth) }
        }
        val response = client.post("/bug-reports") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
            contentType(ContentType.Application.Json)
            setBody("""{"category":"network","description":"Video freezes","context":{"route":"/watch","timestamp":1774200000000,"userAgent":"Mozilla","browserLanguage":"fr-FR"}}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
