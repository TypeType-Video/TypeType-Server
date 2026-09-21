package dev.typetype.server.services

import dev.typetype.server.models.BugReportContextItem

object BugReportValidation {
    private val categories = setOf("player", "audio_language", "subtitles", "ui", "functionality")
    private val statuses = setOf("new", "triaged", "in_progress", "fixed", "closed")

    fun validateCategory(value: String): String? =
        if (value in categories) null else "Invalid category"

    fun validateStatus(value: String): String? =
        if (value in statuses) null else "Invalid status"

    fun validateDescription(value: String): String? {
        val text = value.trim()
        if (text.isEmpty()) return "Description is required"
        if (text.length > 10_000) return "Description is too long"
        return null
    }

    fun validateContext(context: BugReportContextItem): String? {
        if (context.route.isBlank()) return "Context route is required"
        if (context.userAgent.isBlank()) return "Context userAgent is required"
        if (context.browserLanguage.isBlank()) return "Context browserLanguage is required"
        if (context.timestamp <= 0) return "Context timestamp is invalid"
        if (context.crashLogs.size > 200) return "Too many crash logs"
        if (context.apiErrors.size > 100) return "Too many api errors"
        if (context.apiErrors.any { it.endpoint.isBlank() || it.timestamp <= 0 || it.status <= 0 }) {
            return "Invalid api error entry"
        }
        val dimensions = listOf(
            context.viewportWidth,
            context.viewportHeight,
            context.screenWidth,
            context.screenHeight,
        ).filterNotNull()
        if (dimensions.any { it !in 1..100_000 }) return "Invalid display dimensions"
        val devicePixelRatio = context.devicePixelRatio
        if (devicePixelRatio != null && devicePixelRatio !in 0.1..100.0) {
            return "Invalid device pixel ratio"
        }
        val timezone = context.timezone
        if (timezone != null && timezone.length > 128) return "Invalid timezone"
        return null
    }
}
