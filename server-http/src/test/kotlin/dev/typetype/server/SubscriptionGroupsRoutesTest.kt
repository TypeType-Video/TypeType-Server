package dev.typetype.server

import dev.typetype.server.models.SubscriptionGroupItem
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.routes.subscriptionGroupsRoutes
import dev.typetype.server.routes.subscriptionsRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SubscriptionGroupsService
import dev.typetype.server.services.SubscriptionGroupWriteResult
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        private const val FOREIGN_USER_ID = "foreign-user"

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
    fun `group membership projection requires authentication`() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/subscriptions/group-memberships").status)
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
    fun `membership routes add and remove multiple channels atomically`() = withApp {
        val first = channel("one")
        val second = channel("two")
        val neverAdded = channel("three")
        listOf(first, second, neverAdded).forEach { url ->
            subscriptions.add(TEST_USER_ID, SubscriptionItem(url, url.substringAfterLast('/'), ""))
        }
        val group = createGroup("Work")

        assertEquals(HttpStatusCode.NoContent, client.put("/subscriptions/groups/${group.id}/channels") {
            authorizeJson()
            setBody("""{"channelUrls":["$first","$second","$first"]}""")
        }.status)
        assertEquals(HttpStatusCode.NoContent, client.put("/subscriptions/groups/${group.id}/channels") {
            authorizeJson()
            setBody("""{"channelUrls":["$first","$second"]}""")
        }.status)
        val grouped = authorizedGet("/subscriptions") { parameter("groupId", group.id) }.bodyAsText()
        assertTrue(grouped.contains(first))
        assertTrue(grouped.contains(second))
        assertTrue(!grouped.contains(neverAdded))

        assertEquals(HttpStatusCode.NoContent, client.delete("/subscriptions/groups/${group.id}/channels") {
            authorizeJson()
            setBody("""{"channelUrls":["$first","$second","$neverAdded"]}""")
        }.status)
        assertEquals("[]", authorizedGet("/subscriptions") { parameter("groupId", group.id) }.bodyAsText())
    }

    @Test
    fun `membership deletion rejects query and body together`() = withApp {
        val first = channel("one")
        val second = channel("two")
        listOf(first, second).forEach { url ->
            subscriptions.add(TEST_USER_ID, SubscriptionItem(url, url.substringAfterLast('/'), ""))
        }
        val group = createGroup("Work")
        listOf(first, second).forEach { url -> groups.addSubscription(TEST_USER_ID, group.id, url) }

        assertEquals(HttpStatusCode.BadRequest, client.delete("/subscriptions/groups/${group.id}/channels") {
            authorizeJson()
            parameter("url", first)
            setBody("""{"channelUrls":["$second"]}""")
        }.status)
        val grouped = authorizedGet("/subscriptions") { parameter("groupId", group.id) }.bodyAsText()
        assertTrue(grouped.contains(first))
        assertTrue(grouped.contains(second))
    }

    @Test
    fun `bulk membership addition changes nothing when a subscription is missing`() = withApp {
        val subscribed = channel("subscribed")
        subscriptions.add(TEST_USER_ID, SubscriptionItem(subscribed, "Subscribed", ""))
        val group = createGroup("Work")

        assertEquals(HttpStatusCode.NotFound, client.put("/subscriptions/groups/${group.id}/channels") {
            authorizeJson()
            setBody("""{"channelUrls":["$subscribed","${channel("missing")}"]}""")
        }.status)
        assertEquals("[]", authorizedGet("/subscriptions") { parameter("groupId", group.id) }.bodyAsText())
    }

    @Test
    fun `group membership projection returns account scoped memberships with subscription data`() = withApp {
        val sharedChannel = channel("shared")
        subscriptions.add(TEST_USER_ID, SubscriptionItem(sharedChannel, "Shared", "avatar"))
        subscriptions.add(TEST_USER_ID, SubscriptionItem(channel("ungrouped"), "Ungrouped", ""))
        subscriptions.add(FOREIGN_USER_ID, SubscriptionItem(sharedChannel, "Foreign shared", ""))

        val ownGroups = listOf(createGroup("Own"), createGroup("Another"))
        ownGroups.forEach { group ->
            assertEquals(HttpStatusCode.NoContent, client.put("/subscriptions/groups/${group.id}/channels") {
                authorizeJson()
                setBody("""{"channelUrl":"$sharedChannel"}""")
            }.status)
        }
        val foreignGroup = requireNotNull(
            (groups.create(FOREIGN_USER_ID, "Foreign") as? SubscriptionGroupWriteResult.Success)?.group,
        )
        groups.addSubscription(FOREIGN_USER_ID, foreignGroup.id, sharedChannel)

        val response = authorizedGet("/subscriptions/group-memberships")
        assertEquals(HttpStatusCode.OK, response.status)
        val items = Json.parseToJsonElement(response.bodyAsText()).jsonArray.map { it.jsonObject }
        assertEquals(2, items.size)

        val shared = items.single { it.getValue("channelUrl").jsonPrimitive.content == sharedChannel }
        assertEquals("Shared", shared.getValue("name").jsonPrimitive.content)
        assertEquals("avatar", shared.getValue("avatarUrl").jsonPrimitive.content)
        assertTrue(shared.getValue("subscribedAt").jsonPrimitive.content.toLong() > 0)
        assertEquals(
            ownGroups.map { it.id }.sorted(),
            shared.getValue("groupIds").jsonArray.map { it.jsonPrimitive.content },
        )

        val ungrouped = items.single {
            it.getValue("channelUrl").jsonPrimitive.content == channel("ungrouped")
        }
        assertEquals(emptyList<String>(), ungrouped.getValue("groupIds").jsonArray.map { it.jsonPrimitive.content })
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
