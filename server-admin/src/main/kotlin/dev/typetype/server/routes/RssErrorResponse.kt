package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.RssFeedException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

suspend fun ApplicationCall.respondRssError(error: RssFeedException) {
    val status = when (error.code) {
        "rss_feed_not_found", "rss_user_not_found" -> HttpStatusCode.NotFound
        "rss_disabled", "rss_user_disabled" -> HttpStatusCode.Forbidden
        "rss_feed_limit_reached" -> HttpStatusCode.Conflict
        else -> HttpStatusCode.BadRequest
    }
    respond(status, ErrorResponse(error.message ?: "Invalid RSS request", error.code))
}
