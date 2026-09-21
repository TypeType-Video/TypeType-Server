package dev.typetype.server.routes

import dev.typetype.server.models.CreateBugReportRequest
import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.BugReportService
import dev.typetype.server.services.BugReportValidation
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.bugReportRoutes(bugReportService: BugReportService, authService: AuthService) {
    post("/bug-reports") {
        call.withJwtAuth(authService) { userId ->
            if (userId.startsWith("guest:")) {
                return@withJwtAuth call.respond(HttpStatusCode.Forbidden, ErrorResponse("Guest users cannot create bug reports"))
            }
            val body = runCatching { call.receive<CreateBugReportRequest>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            val categoryError = BugReportValidation.validateCategory(body.category)
            if (categoryError != null) return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse(categoryError))
            val descriptionError = BugReportValidation.validateDescription(body.description)
            if (descriptionError != null) return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse(descriptionError))
            val contextError = BugReportValidation.validateContext(body.context)
            if (contextError != null) return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse(contextError))
            call.respond(HttpStatusCode.Created, bugReportService.create(userId, body))
        }
    }
}
