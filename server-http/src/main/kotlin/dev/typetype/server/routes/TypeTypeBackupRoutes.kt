package dev.typetype.server.routes

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.TypeTypeBackupItem
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PipePipeBackupUploadWriter
import dev.typetype.server.services.TypeTypeBackupCategory
import dev.typetype.server.services.TypeTypeBackupService
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.CancellationException
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneOffset

private const val MAX_TYPE_TYPE_BACKUP_BYTES = 128L * 1024 * 1024

fun Route.typeTypeBackupRoutes(service: TypeTypeBackupService, authService: AuthService) {
    get("/backup/typetype") {
        call.withJwtAuth(authService) { userId ->
            val categories = TypeTypeBackupCategory.parse(call.request.queryParameters["categories"])
                ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid backup categories"))
            val filename = "typetype-backup-${LocalDate.now(ZoneOffset.UTC)}.json"
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment
                    .withParameter(ContentDisposition.Parameters.FileName, filename)
                    .toString(),
            )
            call.respond(service.export(userId, categories))
        }
    }
    post("/restore/typetype") {
        call.withJwtAuth(authService) { userId ->
            val tmp = Files.createTempFile("typetype-backup-", ".json")
            try {
                val multipart = call.receiveMultipart(MAX_TYPE_TYPE_BACKUP_BYTES)
                var fileCount = 0
                while (true) {
                    val part = multipart.readPart() ?: break
                    if (part is PartData.FileItem && part.name == "file") {
                        PipePipeBackupUploadWriter.writeWithLimit(
                            part.provider(),
                            tmp,
                            MAX_TYPE_TYPE_BACKUP_BYTES,
                        )
                        fileCount += 1
                    }
                    part.release()
                }
                if (fileCount != 1) {
                    return@withJwtAuth call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Exactly one backup file is required"),
                    )
                }
                val backup = CacheJson.decodeFromString<TypeTypeBackupItem>(Files.readString(tmp))
                call.respond(service.restore(userId, backup))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                call.application.environment.log.info("TypeType restore failed", error)
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid TypeType backup"))
            } finally {
                Files.deleteIfExists(tmp)
            }
        }
    }
}
