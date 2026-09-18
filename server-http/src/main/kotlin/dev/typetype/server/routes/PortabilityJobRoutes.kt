package dev.typetype.server.routes

import dev.typetype.server.portability.PortabilityEngine
import dev.typetype.server.portability.PortabilityImportRequest
import dev.typetype.server.services.AuthService
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

internal fun Route.portabilityJobRoutes(engine: PortabilityEngine, authService: AuthService) {
    get("/portability/jobs/{id}") { withJobAccount(authService) { owner, id -> call.respond(engine.snapshot(owner, id)) } }
    get("/portability/jobs/{id}/report") {
        withJobAccount(authService) { owner, id -> call.respond(engine.report(owner, id)) }
    }
    post("/portability/jobs/{id}/apply") {
        withJobAccount(authService) { owner, id ->
            call.respond(HttpStatusCode.Accepted, engine.applyImport(owner, id, call.receive<PortabilityImportRequest>()))
        }
    }
    post("/portability/jobs/{id}/cancel") {
        withJobAccount(authService) { owner, id -> call.respond(engine.cancel(owner, id)) }
    }
    get("/portability/jobs/{id}/artifact") {
        withJobAccount(authService) { owner, id ->
            val artifact = engine.artifact(owner, id)
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment
                    .withParameter(ContentDisposition.Parameters.FileName, artifact.fileName.toString())
                    .toString(),
            )
            call.respondFile(artifact.toFile())
        }
    }
    delete("/portability/jobs/{id}") {
        withJobAccount(authService) { owner, id ->
            engine.delete(owner, id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private suspend fun io.ktor.server.routing.RoutingContext.withJobAccount(
    authService: AuthService,
    block: suspend (String, String) -> Unit,
) {
    call.withJwtAuth(authService) { userId ->
        call.withPortabilityAccount(userId = userId) { owner ->
            val id = call.parameters["id"]
            if (id == null) {
                call.respondPortabilityError(IllegalArgumentException("Missing portability job id"))
            } else {
                runCatching { block(owner, id) }
                    .onFailure { call.respondPortabilityError(it as? Exception ?: RuntimeException(it)) }
            }
        }
    }
}
