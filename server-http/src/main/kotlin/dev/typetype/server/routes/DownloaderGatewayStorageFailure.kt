package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.requestId
import dev.typetype.server.services.DownloaderGatewayResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val INSUFFICIENT_STORAGE_STATUS = 507
private const val INSUFFICIENT_STORAGE_CODE = "insufficient_storage"
private const val INSUFFICIENT_STORAGE_MESSAGE = "Stockage temporairement sature, reessayez plus tard."

private val STORAGE_DETAIL_NAMES = setOf(
    "availableBytes",
    "dataDir",
    "diskFreeBytes",
    "diskTotalBytes",
    "freeBytes",
    "freePercent",
    "minFreeBytes",
    "requiredFreeBytes",
    "thresholdBytes",
    "totalBytes",
)

internal fun isDownloaderStorageFailure(response: DownloaderGatewayResponse): Boolean =
    response.status == INSUFFICIENT_STORAGE_STATUS

internal suspend fun ApplicationCall.respondDownloaderStorageFailure(response: DownloaderGatewayResponse) {
    val details = downloaderStorageDetails(response.body)
    val detailLog = details.entries.joinToString(separator = " ") { "${it.key}=${it.value}" }
    val suffix = detailLog.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
    application.environment.log.warn(
        "Downloader storage refusal requestId=${requestId()} code=$INSUFFICIENT_STORAGE_CODE$suffix",
    )
    respond(
        HttpStatusCode.fromValue(INSUFFICIENT_STORAGE_STATUS),
        ErrorResponse(INSUFFICIENT_STORAGE_MESSAGE, INSUFFICIENT_STORAGE_CODE),
    )
}

private fun downloaderStorageDetails(body: ByteArray): Map<String, String> {
    val raw = body.toString(Charsets.UTF_8).takeIf { it.isNotBlank() } ?: return emptyMap()
    val root = runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonObject ?: return emptyMap()
    return storageObjects(root)
        .flatMap { it.entries }
        .filter { it.key in STORAGE_DETAIL_NAMES }
        .mapNotNull { (name, value) -> (value as? JsonPrimitive)?.contentOrNull?.let { name to it } }
        .distinctBy { it.first }
        .toMap()
}

private fun storageObjects(root: JsonObject): List<JsonObject> = listOfNotNull(
    root,
    root["details"] as? JsonObject,
    root["disk"] as? JsonObject,
    root["storage"] as? JsonObject,
)
