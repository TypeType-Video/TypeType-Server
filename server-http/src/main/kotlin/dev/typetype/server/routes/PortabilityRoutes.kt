package dev.typetype.server.routes

import dev.typetype.server.portability.PortabilityEngine
import dev.typetype.server.portability.PortabilityExportRequest
import dev.typetype.server.portability.PortabilityUploadWriter
import dev.typetype.server.requestId
import dev.typetype.server.services.AuthService
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

fun Route.portabilityRoutes(engine: PortabilityEngine, authService: AuthService) {
    get("/portability/formats") {
        call.withJwtAuth(authService) { userId ->
            call.withPortabilityAccount(userId = userId) { call.respond(engine.formats()) }
        }
    }
    post("/portability/imports") {
        call.withJwtAuth(authService) { userId ->
            call.withPortabilityAccount(userId = userId) { owner -> call.uploadImport(engine, owner) }
        }
    }
    post("/portability/exports") {
        call.withJwtAuth(authService) { userId ->
            call.withPortabilityAccount(userId = userId) { owner ->
                runCatching {
                    val request = call.receive<PortabilityExportRequest>()
                    engine.startExport(owner, request.format, request.categories, call.requestId())
                }
                    .onSuccess { call.respond(HttpStatusCode.Accepted, it) }
                    .onFailure { call.respondPortabilityError(it.asException()) }
            }
        }
    }
    portabilityJobRoutes(engine, authService)
}

private suspend fun io.ktor.server.application.ApplicationCall.uploadImport(engine: PortabilityEngine, owner: String) {
    val tmp = Files.createTempFile("portability-upload-", ".tmp")
    try {
        val multipart = receiveMultipart(dev.typetype.server.portability.PortabilityLimits.MAX_UPLOAD_BYTES)
        var filename: String? = null
        var contentType: String? = null
        var files = 0
        while (true) {
            val part = multipart.readPart() ?: break
            if (part is PartData.FileItem && part.name == "file") {
                files += 1
                if (files == 1) {
                    filename = part.originalFileName ?: "backup"
                    contentType = part.contentType?.toString()
                    PortabilityUploadWriter.write(part.provider(), tmp)
                }
            }
            part.release()
        }
        require(files == 1) { "Exactly one backup file is required" }
        val hint = parsePortabilityFormat(request.queryParameters["format"])
        respond(
            HttpStatusCode.Accepted,
            engine.startImportPreview(owner, tmp, requireNotNull(filename), contentType, hint, requestId()),
        )
    } catch (error: Exception) {
        respondPortabilityError(error)
    } finally {
        Files.deleteIfExists(tmp)
    }
}

private fun Throwable.asException(): Exception = this as? Exception ?: RuntimeException(this)
