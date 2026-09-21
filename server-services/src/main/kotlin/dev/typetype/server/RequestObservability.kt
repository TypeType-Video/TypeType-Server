package dev.typetype.server

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.util.AttributeKey
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext
import java.util.UUID

private val requestIdAttribute = AttributeKey<String>("requestId")
private val requestStartNanosAttribute = AttributeKey<Long>("requestStartNanos")
private val requestIdRegex = Regex("^[A-Za-z0-9._-]{8,128}$")

fun ApplicationCall.requestId(): String = attributeOrNull(requestIdAttribute) ?: currentRequestId() ?: "unknown"

fun ApplicationCall.requestDurationMs(): Long {
    val startedAt = attributeOrNull(requestStartNanosAttribute) ?: return 0
    return (System.nanoTime() - startedAt).coerceAtLeast(0) / 1_000_000
}

fun Application.installRequestObservability() {
    intercept(ApplicationCallPipeline.Setup) {
        val applicationCall = context
        val requestId = resolveRequestId(applicationCall.request.headers[REQUEST_ID_HEADER])
        applicationCall.attributes.put(requestIdAttribute, requestId)
        applicationCall.attributes.put(requestStartNanosAttribute, System.nanoTime())
        applicationCall.response.headers.append(REQUEST_ID_HEADER, requestId, safeOnly = false)
        withContext(requestContextElement(requestId)) {
            try {
                proceed()
            } finally {
                AppMetrics.record(applicationCall)
            }
        }
    }
}

fun requestLogLine(call: ApplicationCall): String = listOf(
    "requestId=${call.requestId()}",
    "method=${call.request.httpMethod.value}",
    "path=${metricPath(call.request.path())}",
    "status=${call.response.status()?.value ?: 0}",
    "durationMs=${call.requestDurationMs()}",
).joinToString(" ")

private fun resolveRequestId(raw: String?): String =
    raw?.takeIf { requestIdRegex.matches(it) } ?: UUID.randomUUID().toString()

private fun <T : Any> ApplicationCall.attributeOrNull(key: AttributeKey<T>): T? =
    if (attributes.contains(key)) attributes[key] else null
