package dev.typetype.server

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.routes.TooManyRequestsBodyAttribute
import dev.typetype.server.routes.isMultipartSizeLimit
import dev.typetype.server.routes.respondPortabilityError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

fun Application.configureStatusPages() {
    val log = LoggerFactory.getLogger("RequestLogger")
    install(StatusPages) {
        status(HttpStatusCode.TooManyRequests) { call, status ->
            if (call.attributes.contains(TooManyRequestsBodyAttribute)) return@status
            if (!call.response.headers.contains(HttpHeaders.RetryAfter)) call.response.headers.append(HttpHeaders.RetryAfter, "60")
            call.respond(status, ErrorResponse("Too many requests", "rate_limited"))
        }
        exception<IllegalArgumentException> { call, cause ->
            log.warn("Bad request: ${cause.message}")
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Bad request", "bad_request"))
        }
        exception<Throwable> { call, cause ->
            if (cause is io.ktor.utils.io.ClosedWriteChannelException) return@exception
            if (cause is kotlinx.coroutines.CancellationException) throw cause
            // Ktor's multipart producer can fail outside the route's receive block.
            if (call.request.path() == "/portability/imports" && cause.isMultipartSizeLimit()) {
                call.respondPortabilityError(dev.typetype.server.portability.PortabilityUploadTooLargeException())
                return@exception
            }
            log.error("Unhandled exception requestId=${call.requestId()} path=${call.request.path()}", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal server error", "internal_error"))
        }
    }
}

