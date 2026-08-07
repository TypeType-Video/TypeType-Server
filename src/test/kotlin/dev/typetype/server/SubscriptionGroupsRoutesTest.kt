package dev.typetype.server

import dev.typetype.server.models.SubscriptionGroupItem
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.routes.subscriptionGroupsRoutes
import dev.typetype.server.routes.subscriptionsRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SubscriptionGroupsService
import dev.typetype.server.services.SubscriptionsService
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
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
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubscriptionGroupsRoutesTest {
    private val groups = SubscriptionGroupsService()
    private val subscriptions = SubscriptionsService()
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() = TestDatabase.truncateAll()

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing {
                subscriptionGroupsRoutes(groups, auth)
                subscriptionsRoutes(subscriptions, auth, groupsService = groups)
            }
        }
        block()
    }

    @Test
    fun `group routes require authentication`() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/subscriptions/groups").status)
    }

    @Test
    fun `groups can be created listed renamed and deleted`() = withApp {
        val create = client.post("/subscriptions/groups") {
            authorizeJson()
            setBody("""{"name":"Work"}""")
        }
        assertEquals(HttpStatusCode.Created, create.status)
        val group = Json.decodeFromString<SubscriptionGroupItem>(create.bodyAsText())

        assertTrue(authorizedGet("/subscriptions/groups").bodyAsText().contains("\"name\":\"Work\""))
        assertEquals(HttpStatusCode.NoContent, client.put("/subscriptions/groups/${group.id}") {
            authorizeJson()
            setBody("""{"name":"Research"}""")
        }.status)
        assertTrue(authorizedGet("/subscriptions/groups").bodyAsText().contains("\"name\":\"Research\""))
        assertEquals(HttpStatusCode.NoContent, client.delete("/subscriptions/groups/${group.id}") { authorize() }.status)
        assertEquals("[]", authorizedGet("/subscriptions/groups").bodyAsText())
    }

    @Test
    fun `blank and duplicate group names are rejected`() = withApp {
        assertEquals(HttpStatusCode.BadRequest, client.post("/subscriptions/groups") {
            authorizeJson()
            setBody("""{"name":"   "}""")
        }.status)
        assertEquals(HttpStatusCode.Created, client.post("/subscriptions/groups") {
            authorizeJson()
            setBody("""{"name":"Work"}""")
        }.status)
        assertEquals(HttpStatusCode.Conflict, client.post("/subscriptions/groups") {
            authorizeJson()
            setBody("""{"name":"work"}""")
        }.status)
    }

    @Test
    fun `membership drives grouped and ungrouped subscription projections`() = withApp {
        subscriptions.add(TEST_USER_ID, SubscriptionItem(channel("one"), "One", ""))
        subscriptions.add(TEST_USER_ID, SubscriptionItem(channel("two"), "Two", ""))
        val group = createGroup("Work")

        assertEquals(HttpStatusCode.NoContent, client.put("/subscriptions/groups/${group.id}/channels") {
            authorizeJson()
            setBody("""{"channelUrl":"${channel("one")}"}""")
        }.status)

        val grouped = authorizedGet("/subscriptions") { parameter("groupId", group.id) }
        assertTrue(grouped.bodyAsText().contains(channel("one")))
        assertTrue(!grouped.bodyAsText().contains(channel("two")))
        val ungrouped = authorizedGet("/subscriptions") { parameter("ungrouped", true) }
        assertTrue(!ungrouped.bodyAsText().contains(channel("one")))
        assertTrue(ungrouped.bodyAsText().contains(channel("two")))

        assertEquals(HttpStatusCode.NoContent, client.delete("/subscriptions/groups/${group.id}/channels") {
            authorize()
            parameter("url", channel("one"))
        }.status)
        assertTrue(authorizedGet("/subscriptions") { parameter("ungrouped", true) }.bodyAsText().contains(channel("one")))
    }

    @Test
    fun `invalid or inaccessible filters fail explicitly`() = withApp {
        assertEquals(HttpStatusCode.BadRequest, authorizedGet("/subscriptions") {
            parameter("groupId", "group")
            parameter("ungrouped", true)
        }.status)
        assertEquals(HttpStatusCode.NotFound, authorizedGet("/subscriptions") {
            parameter("groupId", "missing")
        }.status)
    }

    private suspend fun ApplicationTestBuilder.createGroup(name: String): SubscriptionGroupItem {
        val response = client.post("/subscriptions/groups") {
            authorizeJson()
            setBody("""{"name":"$name"}""")
        }
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.authorizedGet(
        path: String,
        configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ) = client.get(path) {
        authorize()
        configure()
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authorize() {
        header(HttpHeaders.Authorization, "Bearer test-jwt")
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authorizeJson() {
        authorize()
        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }

    private fun channel(id: String) = "https://yt.com/channel/$id"
}
