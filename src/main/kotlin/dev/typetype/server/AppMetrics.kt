package dev.typetype.server

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object AppMetrics {
    private val totalRequests = AtomicLong()
    private val totalDurationMs = AtomicLong()
    private val statusCounts = ConcurrentHashMap<Int, AtomicLong>()
    private val routeCounts = ConcurrentHashMap<String, AtomicLong>()

    fun record(call: ApplicationCall) {
        val status = call.response.status()?.value ?: 0
        val route = metricPath(call.request.path())
        totalRequests.incrementAndGet()
        totalDurationMs.addAndGet(call.requestDurationMs())
        statusCounts.getOrPut(status) { AtomicLong() }.incrementAndGet()
        routeCounts.getOrPut("$route|$status") { AtomicLong() }.incrementAndGet()
    }

    fun snapshot(): String {
        val total = totalRequests.get()
        val average = if (total == 0L) 0 else totalDurationMs.get() / total
        return buildString {
            appendLine("requests.total=$total")
            appendLine("requests.durationMs.avg=$average")
            statusCounts.toSortedMap().forEach { (status, count) ->
                appendLine("requests.status.$status=${count.get()}")
            }
            routeCounts.toSortedMap().forEach { (key, count) ->
                val (route, status) = key.split('|', limit = 2)
                appendLine("requests.route.${route.metricKey()}.status.$status=${count.get()}")
            }
        }
    }

}

fun metricPath(path: String): String = when {
    path.startsWith("/downloader/jobs/") && path.endsWith("/events") -> "/downloader/jobs/{id}/events"
    path.startsWith("/downloader/jobs/") && path.endsWith("/artifact") -> "/downloader/jobs/{id}/artifact"
    path.startsWith("/downloader/jobs/") && path.endsWith("/cancel") -> "/downloader/jobs/{id}/cancel"
    path.startsWith("/downloader/jobs/") -> "/downloader/jobs/{id}"
    path == "/progress/batch" -> path
    path.startsWith("/progress/") -> "/progress/{videoUrl}"
    path.startsWith("/favorites/") -> "/favorites/{videoUrl}"
    path.startsWith("/watch-later/") -> "/watch-later/{videoUrl}"
    path.startsWith("/subscriptions/") -> "/subscriptions/{channelUrl}"
    else -> path.ifBlank { "/" }
}

private fun String.metricKey(): String = trim('/').ifBlank { "root" }.replace('/', '.')
