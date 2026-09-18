package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.RssFeedEnabledRequest
import dev.typetype.server.models.RssFeedRequest
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.RssFeedException
import dev.typetype.server.services.RssFeedManagementService
import dev.typetype.server.services.RssFeedReadResult
import dev.typetype.server.services.RssFeedReaderService
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

fun Route.rssFeedRoutes(service: RssFeedManagementService, authService: AuthService) {
    get("/rss/feeds") {
        call.withRssUser(authService) { userId -> call.respondNoStore(service.list(userId)) }
    }
    post("/rss/feeds") {
        call.withRssUser(authService) { userId ->
            val body = call.rssBody<RssFeedRequest>() ?: return@withRssUser
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            call.respond(HttpStatusCode.Created, service.create(userId, body))
        }
    }
    put("/rss/feeds/{id}") {
        call.withRssUser(authService) { userId ->
            val id = call.rssFeedId() ?: return@withRssUser
            val body = call.rssBody<RssFeedRequest>() ?: return@withRssUser
            call.respondNoStore(service.update(userId, id, body))
        }
    }
    put("/rss/feeds/{id}/enabled") {
        call.withRssUser(authService) { userId ->
            val id = call.rssFeedId() ?: return@withRssUser
            val body = call.rssBody<RssFeedEnabledRequest>() ?: return@withRssUser
            call.respondNoStore(service.setEnabled(userId, id, body.enabled))
        }
    }
    post("/rss/feeds/{id}/regenerate") {
        call.withRssUser(authService) { userId ->
            val id = call.rssFeedId() ?: return@withRssUser
            call.respondNoStore(service.regenerate(userId, id))
        }
    }
    delete("/rss/feeds/{id}") {
        call.withRssUser(authService) { userId ->
            val id = call.rssFeedId() ?: return@withRssUser
            service.delete(userId, id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

fun Route.rssPublicRoutes(service: RssFeedReaderService) {
    get("/rss/feeds/{file}") {
        val feedId = call.parameters["file"]?.takeIf { it.endsWith(".xml") }?.removeSuffix(".xml")
            ?: return@get call.respond(HttpStatusCode.NotFound)
        val secret = call.request.queryParameters["token"]?.takeIf(String::isNotBlank)
            ?: return@get call.respond(HttpStatusCode.NotFound)
        when (val result = service.read(feedId, secret)) {
            RssFeedReadResult.NotFound -> call.respond(HttpStatusCode.NotFound)
            is RssFeedReadResult.Throttled -> {
                call.response.headers.append(HttpHeaders.RetryAfter, result.retryAfterSeconds.toString())
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("Too many RSS requests", "rss_rate_limited"))
            }
            is RssFeedReadResult.Ready -> call.respondRss(result)
        }
    }
}

internal suspend fun ApplicationCall.respondRssError(error: RssFeedException) {
    val status = when (error.code) {
        "rss_feed_not_found", "rss_user_not_found" -> HttpStatusCode.NotFound
        "rss_disabled", "rss_user_disabled" -> HttpStatusCode.Forbidden
        "rss_feed_limit_reached" -> HttpStatusCode.Conflict
        else -> HttpStatusCode.BadRequest
    }
    respond(status, ErrorResponse(error.message ?: "Invalid RSS request", error.code))
}

private suspend inline fun ApplicationCall.withRssUser(
    authService: AuthService,
    crossinline block: suspend (String) -> Unit,
) {
    try {
        withJwtAuth(authService) { userId ->
            if (userId.startsWith("guest:")) {
                return@withJwtAuth respond(HttpStatusCode.Forbidden, ErrorResponse("Guest users cannot manage RSS feeds"))
            }
            block(userId)
        }
    } catch (error: RssFeedException) {
        respondRssError(error)
    }
}

private suspend inline fun <reified T : Any> ApplicationCall.rssBody(): T? = runCatching { receive<T>() }.getOrElse {
    respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body", "rss_invalid_body"))
    null
}

private suspend fun ApplicationCall.rssFeedId(): String? = parameters["id"] ?: run {
    respond(HttpStatusCode.BadRequest, ErrorResponse("Missing RSS feed id", "rss_missing_feed_id"))
    null
}

private suspend fun ApplicationCall.respondRss(result: RssFeedReadResult.Ready) {
    response.headers.append(HttpHeaders.ETag, result.etag)
    response.headers.append(HttpHeaders.LastModified, RFC_1123.format(Instant.ofEpochMilli(result.lastModified)))
    response.headers.append(HttpHeaders.CacheControl, "private, max-age=${result.maxAgeSeconds}, must-revalidate")
    val ifNoneMatch = request.headers[HttpHeaders.IfNoneMatch]
    val unchanged = if (ifNoneMatch != null) {
        etagMatches(ifNoneMatch, result.etag)
    } else {
        request.headers[HttpHeaders.IfModifiedSince]?.let(::parseHttpDate)?.let { since ->
            !Instant.ofEpochMilli(result.lastModified).truncatedTo(ChronoUnit.SECONDS).isAfter(since)
        } == true
    }
    if (unchanged) return respond(HttpStatusCode.NotModified)
    respondBytes(result.bytes, ContentType.parse("application/rss+xml; charset=utf-8"))
}

private fun parseHttpDate(value: String): Instant? = runCatching { Instant.from(RFC_1123.parse(value)) }.getOrNull()
private fun etagMatches(header: String, etag: String): Boolean = header.split(',').any { candidate ->
    candidate.trim().let { it == "*" || it.removePrefix("W/") == etag.removePrefix("W/") }
}
private val RFC_1123 = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC)
