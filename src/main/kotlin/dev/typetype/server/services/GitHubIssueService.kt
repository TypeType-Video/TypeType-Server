package dev.typetype.server.services

import dev.typetype.server.models.AdminBugReportDetailResponse
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed interface GitHubIssueCreateResult {
    data class Success(val url: String) : GitHubIssueCreateResult
    data class Failure(val message: String) : GitHubIssueCreateResult
}

fun interface BugReportGitHubIssueService {
    suspend fun createIssue(report: AdminBugReportDetailResponse): GitHubIssueCreateResult
}

class GitHubIssueService(
    private val repo: String = System.getenv("GITHUB_REPO")?.takeIf { it.isNotBlank() } ?: DEFAULT_REPO,
    private val template: String? = System.getenv("GITHUB_ISSUE_TEMPLATE")?.takeIf { it.isNotBlank() },
) : BugReportGitHubIssueService {
    override suspend fun createIssue(report: AdminBugReportDetailResponse): GitHubIssueCreateResult {
        if (!repo.matches(REPO_PATTERN)) return GitHubIssueCreateResult.Failure("GitHub repository is invalid")
        val title = "[Bug][${report.category}] ${redactDomains(report.description).take(80)}"
        val body = buildIssueBody(report)
        val url = buildIssueUrl(title = title, body = body)
        return GitHubIssueCreateResult.Success(url)
    }

    private fun buildIssueBody(report: AdminBugReportDetailResponse): String = buildString {
        appendLine("## Bug report")
        appendLine(redactDomains(report.description))
        appendLine()
        appendLine("## Context")
        appendLine("- User id: ${report.userId}")
        appendLine("- Route: ${redactDomains(report.context.route)}")
        appendLine("- Video: ${redactDomains(report.context.videoUrl ?: "n/a")}")
        appendLine("- Browser language: ${report.context.browserLanguage}")
        appendLine("- User agent: ${redactDomains(report.context.userAgent)}")
        appendLine("- Timestamp: ${report.context.timestamp}")
        appendLine("- Crash logs: ${report.context.crashLogs.size}")
        appendLine("- API errors: ${report.context.apiErrors.size}")
        val recentApiError = report.context.apiErrors.firstOrNull()
        if (recentApiError != null) {
            appendLine("- Latest API endpoint: ${redactDomains(recentApiError.endpoint)}")
            appendLine("- Latest API status: ${recentApiError.status}")
            appendLine("- Latest API requestId: ${redactDomains(recentApiError.requestId ?: "n/a")}")
            appendLine("- Latest API code: ${recentApiError.code ?: "n/a"}")
        }
        appendLine()
        appendLine("## Player state")
        appendLine(report.context.playerState?.toString() ?: "{}")
        if (report.context.apiErrors.isNotEmpty()) {
            appendLine()
            appendLine("## API errors")
            report.context.apiErrors.take(10).forEach { error ->
                appendLine("- endpoint=${redactDomains(error.endpoint)} status=${error.status} requestId=${redactDomains(error.requestId ?: "n/a")} code=${error.code ?: "n/a"} timestamp=${error.timestamp}")
                val message = error.message
                if (!message.isNullOrBlank()) appendLine("  message=${redactDomains(message)}")
            }
        }
    }

    private fun buildIssueUrl(title: String, body: String): String {
        val params = mutableListOf(
            "title=${encode(title)}",
            "body=${encode(body)}",
            "labels=${encode("bug")}",
        )
        if (template != null) params += "template=${encode(template)}"
        return "https://github.com/$repo/issues/new?${params.joinToString("&")}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun redactDomains(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return "n/a"
        val asUri = runCatching { URI(trimmed) }.getOrNull()
        if (asUri != null && !asUri.host.isNullOrBlank()) return sanitizeHostTokens(uriWithoutHost(asUri))
        val withoutUrls = urlRegex.replace(trimmed) { match ->
            val uri = runCatching { URI(match.value) }.getOrNull()
            if (uri == null || uri.host.isNullOrBlank()) match.value else uriWithoutHost(uri)
        }
        return sanitizeHostTokens(withoutUrls)
    }

    private fun sanitizeHostTokens(value: String): String =
        hostTokenRegex.replace(value) { REDACTED_HOST }

    private fun uriWithoutHost(uri: URI): String {
        val path = uri.rawPath?.takeIf { it.isNotBlank() } ?: "/"
        val query = uri.rawQuery?.let { "?$it" } ?: ""
        val fragment = uri.rawFragment?.let { "#$it" } ?: ""
        return "$path$query$fragment"
    }

    private companion object {
        const val REDACTED_HOST: String = "redacted-host"
        const val DEFAULT_REPO: String = "TypeType-Video/TypeType"
        val REPO_PATTERN = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
        val urlRegex = Regex("https?://[^\\s)]+")
        val hostTokenRegex = Regex("(?<![@/])\\b(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,63}\\b")
    }
}
