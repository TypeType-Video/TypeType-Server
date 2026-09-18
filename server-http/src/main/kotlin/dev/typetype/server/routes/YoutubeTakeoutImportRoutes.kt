package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.YoutubeTakeoutCommitRequest
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.YoutubeTakeoutImportJobService
import dev.typetype.server.services.YoutubeTakeoutLimits
import dev.typetype.server.services.YoutubeTakeoutUploadWriter
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.nio.file.Files

fun Route.youtubeTakeoutImportRoutes(
    importService: YoutubeTakeoutImportJobService,
    authService: AuthService,
    maxUploadBytes: Long = YoutubeTakeoutLimits.MAX_UPLOAD_BYTES,
) {
    post("/imports/youtube-takeout") {
        call.withJwtAuth(authService) { userId ->
            val tmp = Files.createTempFile("youtube-takeout-", ".zip")
            try {
                val multipart = call.receiveMultipart(maxUploadBytes)
                var hasFile = false
                while (true) {
                    val part = multipart.readPart() ?: break
                    if (part is PartData.FileItem && part.name == "archive") {
                        if (!part.originalFileName.orEmpty().endsWith(".zip", true)) {
                            part.release()
                            return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid archive file type"))
                        }
                        YoutubeTakeoutUploadWriter.writeWithLimit(part.provider(), tmp, maxUploadBytes)
                        hasFile = true
                    }
                    part.release()
                }
                if (!hasFile) return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing archive part"))
                call.respond(HttpStatusCode.Created, importService.create(userId, tmp))
            } catch (e: Exception) {
                Files.deleteIfExists(tmp)
                when (e) {
                    is IllegalArgumentException,
                    is IllegalStateException,
                    -> {
                        val message = e.message ?: "Invalid takeout archive"
                        val status = if (message.contains("too large", ignoreCase = true)) HttpStatusCode.PayloadTooLarge else HttpStatusCode.BadRequest
                        call.respond(status, ErrorResponse(message))
                    }

                    else -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid takeout archive"))
                }
            }
        }
    }

    get("/imports/youtube-takeout/{jobId}") {
        call.withJwtAuth(authService) { userId ->
            val jobId = call.parameters["jobId"] ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing jobId"))
            val status = importService.get(userId, jobId)
            if (status == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("Import job not found")) else call.respond(status)
        }
    }

    get("/imports/youtube-takeout/{jobId}/preview") {
        call.withJwtAuth(authService) { userId ->
            val jobId = call.parameters["jobId"] ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing jobId"))
            runCatching { importService.preview(userId, jobId) }
                .onSuccess { call.respond(it) }
                .onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Preview failed")) }
        }
    }

    post("/imports/youtube-takeout/{jobId}/commit") {
        call.withJwtAuth(authService) { userId ->
            val jobId = call.parameters["jobId"] ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing jobId"))
            val request = runCatching { call.receive<YoutubeTakeoutCommitRequest>() }.getOrNull()
            runCatching { importService.commit(userId, jobId, request) }
                .onSuccess { call.respond(HttpStatusCode.Accepted, it) }
                .onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Commit failed")) }
        }
    }

    get("/imports/youtube-takeout/{jobId}/report") {
        call.withJwtAuth(authService) { userId ->
            val jobId = call.parameters["jobId"] ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing jobId"))
            val report = runCatching { importService.report(userId, jobId) }.getOrNull()
            if (report == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("Report not available")) else call.respond(report)
        }
    }
}
