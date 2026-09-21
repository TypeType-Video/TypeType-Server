package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.SubscriptionGroupMembershipRequest
import dev.typetype.server.services.SubscriptionGroupsService
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream

private const val MAX_MEMBERSHIP_REQUEST_BODY_BYTES = 1024 * 1024
private const val MAX_MEMBERSHIP_CHANNEL_URL_LENGTH = 2048
private val membershipRequestJson = Json { ignoreUnknownKeys = true }

internal sealed interface MembershipChannels {
    data class Single(val channelUrl: String) : MembershipChannels
    data class Batch(val channelUrls: List<String>) : MembershipChannels
}

internal suspend fun ApplicationCall.receiveMembershipChannels(body: ByteArray): MembershipChannels? {
    val request = if (request.contentType().match(ContentType.Application.Json)) {
        try {
            membershipRequestJson.decodeFromString<SubscriptionGroupMembershipRequest>(body.decodeToString())
        } catch (_: SerializationException) {
            null
        }
    } else {
        null
    }
    val channelUrl = request?.channelUrl?.takeIf(String::isValidMembershipChannelUrl)
    val channelUrls = request?.channelUrls
    val parsed = when {
        channelUrl != null && channelUrls == null -> MembershipChannels.Single(channelUrl)
        request?.channelUrl == null && channelUrls != null &&
            channelUrls.size in 1..SubscriptionGroupsService.MAX_MEMBERSHIP_CHANNELS &&
            channelUrls.all(String::isValidMembershipChannelUrl) -> MembershipChannels.Batch(channelUrls)
        else -> null
    }
    if (parsed == null) respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
    return parsed
}

internal suspend fun ApplicationCall.receiveMembershipBody(): ByteArray? {
    val contentLength = request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (contentLength != null && contentLength > MAX_MEMBERSHIP_REQUEST_BODY_BYTES) {
        respondMembershipBodyTooLarge()
        return null
    }
    val body = receiveChannel().readUpTo(MAX_MEMBERSHIP_REQUEST_BODY_BYTES)
    if (body == null) respondMembershipBodyTooLarge()
    return body
}

private suspend fun ByteReadChannel.readUpTo(maxBytes: Int): ByteArray? = withContext(Dispatchers.IO) {
    toInputStream().use { input ->
        ByteArrayOutputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                if (output.size() + read > maxBytes) return@withContext null
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }
}

internal fun String.isValidMembershipChannelUrl(): Boolean =
    isNotBlank() && length <= MAX_MEMBERSHIP_CHANNEL_URL_LENGTH

private suspend fun ApplicationCall.respondMembershipBodyTooLarge() = respond(
    HttpStatusCode.PayloadTooLarge,
    ErrorResponse("Request body exceeds 1 MiB", "request_body_too_large"),
)
