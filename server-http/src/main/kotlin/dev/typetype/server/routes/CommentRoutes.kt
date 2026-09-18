package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.CommentService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.commentRoutes(
    commentService: CommentService,
    authService: AuthService? = null,
    adminSettingsService: AdminSettingsService? = null,
) {
    get("/comments") {
        if (!call.requirePublicAccessOrRespond(authService, adminSettingsService)) return@get
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))
        val nextpage = call.request.queryParameters["nextpage"]

        when (val result = commentService.getComments(url = url, nextpage = nextpage)) {
            is ExtractionResult.Success -> call.respond(result.data)
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
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
            is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
        }
    }
}
