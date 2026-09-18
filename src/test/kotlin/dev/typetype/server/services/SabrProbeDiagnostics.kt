package dev.typetype.server.services

private const val DEFAULT_SABR_PROBE_VIDEO_ID = "MO7hCeL-zRU"
private const val DEFAULT_SABR_PROBE_PLAYER_TIME_MS = 321_601L
private const val DEFAULT_SABR_PROBE_VIDEO_ITAG = 137
private const val DEFAULT_SABR_PROBE_AUDIO_ITAG = 140
private const val DEFAULT_SABR_PROBE_TIMEOUT_MS = 60_000L

internal fun sabrProbeTokenServiceUrl(): String =
    envValue("SUBTITLE_SERVICE_URL") ?: "http://localhost:8081"

internal fun sabrProbeVideoId(): String =
    envValue("SABR_PROBE_VIDEO")
        ?: envValues("SABR_PROBE_VIDEOS").firstOrNull()
        ?: DEFAULT_SABR_PROBE_VIDEO_ID

internal fun sabrProbeVideoIds(): List<String> =
    envValues("SABR_PROBE_VIDEOS").ifEmpty { listOf(sabrProbeVideoId()) }

internal fun sabrProbePlayerTimeMs(): Long =
    envValue("SABR_PROBE_PLAYER_TIME_MS")?.toLongOrNull()?.takeIf { it >= 0L }
        ?: DEFAULT_SABR_PROBE_PLAYER_TIME_MS

internal fun sabrProbeTimeoutMs(): Long =
    envValue("SABR_PROBE_FETCH_TIMEOUT_MS")?.toLongOrNull()?.takeIf { it > 0L }
        ?: envValue("SABR_PROBE_TIMEOUT_MS")?.toLongOrNull()?.takeIf { it > 0L }
        ?: DEFAULT_SABR_PROBE_TIMEOUT_MS

internal fun sabrProbeAudioItag(): Int =
    envValue("SABR_PROBE_AUDIO_ITAG")?.toIntOrNull() ?: DEFAULT_SABR_PROBE_AUDIO_ITAG

internal fun sabrProbeVideoItags(): List<Int> {
    val explicit = envIntValues("SABR_PROBE_VIDEO_ITAGS")
    val primary = envValue("SABR_PROBE_VIDEO_ITAG")?.toIntOrNull() ?: DEFAULT_SABR_PROBE_VIDEO_ITAG
    val base = explicit.ifEmpty { listOf(primary) }
    val extra720 = envValue("SABR_PROBE_720P_ITAG")?.toIntOrNull()
    return (base + listOfNotNull(extra720)).distinct()
}

private fun envValue(name: String): String? =
    System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }

private fun envValues(name: String): List<String> =
    envValue(name)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

private fun envIntValues(name: String): List<Int> =
    envValues(name).mapNotNull { it.toIntOrNull() }
