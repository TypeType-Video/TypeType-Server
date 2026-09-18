package dev.typetype.server.routes

import dev.typetype.server.models.AdminBugReportsPageResponse
import dev.typetype.server.models.BugReportGithubIssueResponse
import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.UpdateBugReportStatusRequest
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.BugReportService
import dev.typetype.server.services.BugReportStatusUpdateResult
import dev.typetype.server.services.BugReportValidation
import dev.typetype.server.services.BugReportGitHubIssueService
import dev.typetype.server.services.GitHubIssueCreateResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put

fun Route.adminBugReportRoutes(
    authService: AuthService,
    bugReportService: BugReportService,
    gitHubIssueService: BugReportGitHubIssueService,
) {
    get("/admin/bug-reports") {
        call.withAdminModeratorAuth(authService) { _ ->
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            if (page < 1) return@withAdminModeratorAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid page"))
            if (limit !in 1..100) return@withAdminModeratorAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid limit"))
            val status = call.request.queryParameters["status"]
            if (status != null && BugReportValidation.validateStatus(status) != null) {
                return@withAdminModeratorAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid status"))
            }
            val category = call.request.queryParameters["category"]
            if (category != null && BugReportValidation.validateCategory(category) != null) {
                return@withAdminModeratorAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid category"))
            }
            val (items, total) = bugReportService.list(page, limit, status, category)
            call.respond(AdminBugReportsPageResponse(items = items, page = page, limit = limit, total = total))
        }
    }

    get("/admin/bug-reports/{id}") {
        call.withAdminModeratorAuth(authService) { _ ->
            val id = call.parameters["id"] ?: return@withAdminModeratorAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val detail = bugReportService.detail(id)
            if (detail == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("Bug report not found")) else call.respond(detail)
        }
    }

    put("/admin/bug-reports/{id}/status") {
        call.withAdminModeratorAuth(authService) { _ ->
            val id = call.parameters["id"] ?: return@withAdminModeratorAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val body = runCatching { call.receive<UpdateBugReportStatusRequest>() }.getOrElse {
                return@withAdminModeratorAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            val statusError = BugReportValidation.validateStatus(body.status)
            if (statusError != null) return@withAdminModeratorAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse(statusError))
            when (val update = bugReportService.updateStatus(id, body.status)) {
                is BugReportStatusUpdateResult.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorResponse("Bug report not found"))
                is BugReportStatusUpdateResult.InvalidTransition -> {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid status transition: ${update.currentStatus} -> ${update.requestedStatus}"))
                }
                is BugReportStatusUpdateResult.Updated -> call.respond(update.response)
            }
        }
    }

    post("/admin/bug-reports/{id}/github-issue") {
        call.withAdminAuth(authService) { _ ->
            val id = call.parameters["id"] ?: return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val report = bugReportService.detail(id)
                ?: return@withAdminAuth call.respond(HttpStatusCode.NotFound, ErrorResponse("Bug report not found"))
            if (report.githubIssueUrl != null) {
                return@withAdminAuth call.respond(
                    HttpStatusCode.Conflict,
                    mapOf("error" to "GitHub issue already exists", "githubIssueUrl" to report.githubIssueUrl),
                )
            }
            when (val result = gitHubIssueService.createIssue(report)) {
                is GitHubIssueCreateResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
                is GitHubIssueCreateResult.Success -> {
                    val updatedAt = bugReportService.markGithubIssue(id, result.url)
                    if (updatedAt == null) {
                        val existingUrl = bugReportService.existingGithubIssue(id)
                        if (existingUrl != null) {
                            call.respond(HttpStatusCode.Conflict, mapOf("error" to "GitHub issue already exists", "githubIssueUrl" to existingUrl))
                        } else {
                            call.respond(HttpStatusCode.NotFound, ErrorResponse("Bug report not found"))
                        }
                    } else {
                        call.respond(HttpStatusCode.Created, BugReportGithubIssueResponse(id = id, githubIssueUrl = result.url, updatedAt = updatedAt))
                    }
                }
            }
        }
    }
}
