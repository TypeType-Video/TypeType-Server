package dev.typetype.server

import dev.typetype.server.GitHubIssueServiceTestReports.sampleReport
import dev.typetype.server.GitHubIssueServiceTestReports.sampleReportWithDomain
import dev.typetype.server.GitHubIssueServiceTestReports.sampleReportWithHostTokens
import dev.typetype.server.services.GitHubIssueCreateResult
import dev.typetype.server.services.GitHubIssueService
import kotlinx.coroutines.runBlocking
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GitHubIssueServiceTest {
    @Test
    fun `default repo targets central repository`() = runBlocking {
        val service = GitHubIssueService(repo = "TypeType-Video/TypeType")
        val result = service.createIssue(sampleReport())
        assertTrue(result is GitHubIssueCreateResult.Success)
        val url = (result as GitHubIssueCreateResult.Success).url
        assertTrue(url.startsWith("https://github.com/TypeType-Video/TypeType/issues/new?"))
    }

    @Test
    fun `issue body includes api error diagnostics`() = runBlocking {
        val service = GitHubIssueService(repo = "TypeType-Video/TypeType")
        val result = service.createIssue(sampleReport())
        val url = (result as GitHubIssueCreateResult.Success).url
        assertTrue(url.contains("API+errors"))
        assertTrue(url.contains("requestId%3Dreq-123"))
        assertTrue(url.contains("code%3DBAD_REQUEST"))
    }

    @Test
    fun `issue body redacts domains from urls and identifiers`() = runBlocking {
        val service = GitHubIssueService(repo = "TypeType-Video/TypeType")
        val result = service.createIssue(sampleReportWithDomain())
        val url = URLDecoder.decode((result as GitHubIssueCreateResult.Success).url, StandardCharsets.UTF_8)
        assertTrue(url.contains("/watch?url=abc"), url)
        assertTrue(url.contains("/api/streams?url=1"), url)
        assertTrue(!url.contains("internal.local"), url)
        assertTrue(!url.contains("private.example"), url)
    }

    @Test
    fun `issue body redacts hostile requestId and userAgent host tokens`() = runBlocking {
        val service = GitHubIssueService(repo = "TypeType-Video/TypeType")
        val result = service.createIssue(sampleReportWithHostTokens())
        val url = URLDecoder.decode((result as GitHubIssueCreateResult.Success).url, StandardCharsets.UTF_8)
        assertTrue(!url.contains("private.host.internal"), url)
        assertTrue(!url.contains("req.private.local"), url)
        assertTrue(url.contains("requestId=redacted-host"), url)
        assertTrue(url.contains("User agent: Mozilla (redacted-host/client)"), url)
    }
}
