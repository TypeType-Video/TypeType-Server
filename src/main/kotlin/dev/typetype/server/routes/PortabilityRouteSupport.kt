package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.portability.PortabilityContractException
import dev.typetype.server.portability.PortabilityFormat
import dev.typetype.server.portability.PortabilityJobNotFoundException
import dev.typetype.server.portability.PortabilityUploadTooLargeException
import dev.typetype.server.portability.portabilityErrorCode
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

internal fun parsePortabilityFormat(value: String?): PortabilityFormat? {
    if (value == null) return null
    return PortabilityFormat.entries.firstOrNull { it.wireName == value }
        ?: throw IllegalArgumentException("Unsupported portability format")
}

internal suspend fun ApplicationCall.respondPortabilityError(error: Exception) {
    if (error is kotlinx.coroutines.CancellationException) throw error
    if (error.isMultipartSizeLimit()) {
        respondPortabilityError(PortabilityUploadTooLargeException())
        return
    }
    val status = when (error) {
        is PortabilityJobNotFoundException -> HttpStatusCode.NotFound
        is PortabilityUploadTooLargeException -> HttpStatusCode.PayloadTooLarge
        is IllegalStateException -> HttpStatusCode.Conflict
        is IllegalArgumentException -> HttpStatusCode.BadRequest
        else -> HttpStatusCode.InternalServerError
    }
    val message = if (error is PortabilityContractException || error is IllegalArgumentException) {
        error.message ?: "Invalid portability request"
    } else {
        "Portability operation failed"
    }
    respond(status, ErrorResponse(message, portabilityErrorCode(error)))
}

internal suspend inline fun ApplicationCall.withPortabilityAccount(
    userId: String,
    crossinline block: suspend (String) -> Unit,
) {
    if (userId.startsWith("guest:")) {
        respond(HttpStatusCode.Forbidden, ErrorResponse("Guest users do not have portable account data"))
        return
    }
    block(userId)
}
