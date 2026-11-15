package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.TwitchLiveResponse
import dev.typetype.server.services.TwitchService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private val loginPattern = Regex("^[a-zA-Z0-9_]{1,25}$")
private val slugPattern = Regex("^[a-zA-Z0-9_-]{1,100}$")
private val idPattern = Regex("^[0-9]{1,20}$")

// Twitch tags are one word of letters and digits in any script, as TypeType-Live checks again.
private val tagPattern = Regex("^[\\p{L}\\p{Nd}]{1,25}$")
private val pageParams = listOf("first", "after")

fun Route.twitchRoutes(
    service: TwitchService,
    authService: AuthService,
    adminSettingsService: AdminSettingsService? = null,
) {
    suspend fun ApplicationCall.relay(path: String, params: List<String> = pageParams, withUser: Boolean = false) {
        if (!twitchEnabled(service)) return
        val access = accessProfileOrRespond(authService, null, adminSettingsService) ?: return
        val query = params.mapNotNull { name -> request.queryParameters[name]?.let { name to it.take(MAX_PARAM) } }.toMap()
        val userId = access.userId?.takeIf { withUser && !it.startsWith("guest:") }
        respondLive(service.relay(path, query, userId))
    }

    get("/twitch/streams") { call.relay("/v1/streams") }
    get("/twitch/games") { call.relay("/v1/games") }
    get("/twitch/games/{slug}/streams") {
        val slug = call.pathParam("slug", slugPattern) ?: return@get
        call.relay("/v1/games/$slug/streams")
    }
    get("/twitch/tags/{tag}/streams") {
        val tag = call.pathParam("tag", tagPattern) ?: return@get
        call.relay("/v1/tags/$tag/streams")
    }
    get("/twitch/channels/{login}") {
        val login = call.pathParam("login", loginPattern) ?: return@get
        call.relay("/v1/channels/$login", emptyList())
    }
    get("/twitch/channels/{login}/videos") {
        val login = call.pathParam("login", loginPattern) ?: return@get
        call.relay("/v1/channels/$login/videos")
    }
    get("/twitch/channels/{login}/clips") {
        val login = call.pathParam("login", loginPattern) ?: return@get
        call.relay("/v1/channels/$login/clips")
    }
    get("/twitch/live/{login}") {
        val login = call.pathParam("login", loginPattern) ?: return@get
        call.relay("/v1/live/$login", emptyList(), withUser = true)
    }
    get("/twitch/videos/{id}") {
        val id = call.pathParam("id", idPattern) ?: return@get
        call.relay("/v1/videos/$id", emptyList(), withUser = true)
    }
    get("/twitch/videos/{id}/comments") {
        val id = call.pathParam("id", idPattern) ?: return@get
        call.relay("/v1/videos/$id/comments", listOf("offset", "cursor"))
    }
    get("/twitch/clips/{slug}") {
        val slug = call.pathParam("slug", slugPattern) ?: return@get
        call.relay("/v1/clips/$slug", emptyList())
    }
    get("/twitch/search") { call.relay("/v1/search", listOf("q", "type", "first", "after")) }
    get("/twitch/chat/assets") { call.relay("/v1/chat/assets", emptyList()) }
    get("/twitch/chat/{login}/assets") {
        val login = call.pathParam("login", loginPattern) ?: return@get
        call.relay("/v1/chat/$login/assets", emptyList())
    }
    twitchAccountRoutes(service, authService, adminSettingsService)
}

internal suspend fun ApplicationCall.twitchEnabled(service: TwitchService): Boolean {
    if (service.enabled) return true
    respond(HttpStatusCode.NotFound, ErrorResponse("Twitch is not enabled on this instance", "twitch_disabled"))
    return false
}

internal suspend fun ApplicationCall.respondLive(response: TwitchLiveResponse) {
    respondText(response.body, ContentType.Application.Json, HttpStatusCode.fromValue(response.status))
}

internal suspend fun ApplicationCall.pathParam(name: String, pattern: Regex): String? {
    val value = parameters[name]?.takeIf { pattern.matches(it) }
    if (value == null) respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid $name"))
    return value
}

internal val twitchLoginPattern: Regex get() = loginPattern

private const val MAX_PARAM = 200
