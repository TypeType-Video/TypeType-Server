package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PipePipeBackupImporterService
import dev.typetype.server.services.PipePipeBackupLimits
import dev.typetype.server.services.PipePipeBackupTimeMode
import dev.typetype.server.services.PipePipeBackupUploadWriter
import dev.typetype.server.services.PipePipeBackupValidators
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.nio.file.Files

fun Route.restoreRoutes(restoreService: PipePipeBackupImporterService, authService: AuthService) {
    post("/restore/pipepipe") {
        call.withJwtAuth(authService) { userId ->
            val timeModeRaw = call.request.queryParameters["timeMode"]
            val timeMode = PipePipeBackupTimeMode.fromQuery(timeModeRaw)
                ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid timeMode"))
            val tmp = Files.createTempFile("pipepipe-backup-", ".zip")
            try {
                val multipart = call.receiveMultipart(PipePipeBackupLimits.MAX_UPLOAD_BYTES)
                var hasFile = false
                var fileCount = 0
                while (true) {
                    val part = multipart.readPart() ?: break
                    if (part is PartData.FileItem && part.name == "file") {
                        if (!PipePipeBackupValidators.isZipFilePart(part)) {
                            part.release()
                            return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid backup file type"))
                        }
                        PipePipeBackupUploadWriter.writeWithLimit(part.provider(), tmp, PipePipeBackupLimits.MAX_UPLOAD_BYTES)
                        hasFile = true
                        fileCount += 1
                    }
                    part.release()
                }
                if (fileCount > 1) {
                    return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Only one backup file is allowed"))
                }
                if (!hasFile) {
                    return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing file part"))
                }
                val result = restoreService.restore(userId, tmp, timeMode)
                call.respond(result)
            } catch (e: Exception) {
                call.application.environment.log.info("Restore backup failed", e)
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid backup archive"))
            } finally {
                Files.deleteIfExists(tmp)
            }
        }
    }
}
