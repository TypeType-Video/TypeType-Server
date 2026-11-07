package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.CommentService
import dev.typetype.server.services.CommentSearchQuery
import dev.typetype.server.services.CommentSearchService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.commentRoutes(
    commentService: CommentService,
    authService: AuthService? = null,
    adminSettingsService: AdminSettingsService? = null,
    commentSearchService: CommentSearchService = CommentSearchService(commentService),
) {
    get("/comments/search") {
        if (!call.requirePublicAccessOrRespond(authService, adminSettingsService)) return@get
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))
        val query = CommentSearchQuery.parse(call.request.queryParameters["q"])
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid 'q' parameter"))
        val cursor = call.request.queryParameters["cursor"]

        when (val result = commentSearchService.search(url = url, query = query, cursor = cursor)) {
            is ExtractionResult.Success -> call.respond(result.data)
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            is ExtractionResult.Failure -> call.respondExtractionFailure(result, youtube = false)
        }
    }

    get("/comments") {
        if (!call.requirePublicAccessOrRespond(authService, adminSettingsService)) return@get
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))
        val nextpage = call.request.queryParameters["nextpage"]

        when (val result = commentService.getComments(url = url, nextpage = nextpage)) {
            is ExtractionResult.Success -> call.respond(result.data)
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            is ExtractionResult.Failure -> call.respondExtractionFailure(result, youtube = false)
        }
    }

    get("/comments/replies") {
        if (!call.requirePublicAccessOrRespond(authService, adminSettingsService)) return@get
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))
        val repliesPage = call.request.queryParameters["repliesPage"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'repliesPage' parameter"))

        when (val result = commentService.getComments(url = url, nextpage = repliesPage)) {
            is ExtractionResult.Success -> call.respond(result.data)
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            is ExtractionResult.Failure -> call.respondExtractionFailure(result, youtube = false)
        }
    }
}
