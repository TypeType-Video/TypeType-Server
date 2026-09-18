package dev.typetype.server.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class BugCrashLogItem(
    val message: String,
    val stack: String? = null,
    val timestamp: Long,
)

@Serializable
data class BugApiErrorItem(
    val requestId: String? = null,
    val endpoint: String,
    val status: Int,
    val code: String? = null,
    val message: String? = null,
    val timestamp: Long,
)

@Serializable
data class BugReportContextItem(
    val videoUrl: String? = null,
    val route: String,
    val timestamp: Long,
    val userAgent: String,
    val browserLanguage: String,
    val viewportWidth: Int? = null,
    val viewportHeight: Int? = null,
    val screenWidth: Int? = null,
    val screenHeight: Int? = null,
    val devicePixelRatio: Double? = null,
    val online: Boolean? = null,
    val timezone: String? = null,
    val playerState: JsonElement? = null,
    val crashLogs: List<BugCrashLogItem> = emptyList(),
    val apiErrors: List<BugApiErrorItem> = emptyList(),
)
