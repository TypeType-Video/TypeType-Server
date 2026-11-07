package dev.typetype.server

import dev.typetype.server.models.CommentItem
import dev.typetype.server.models.CommentsPageResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.CommentSearchQuery
import dev.typetype.server.services.CommentSearchService
import dev.typetype.server.services.CommentService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommentSearchServiceTest {
    private fun comment(id: String, text: String, author: String = "someone") = CommentItem(
        id = id, text = text, author = author, authorUrl = "", authorAvatarUrl = "", likeCount = 0,
        textualLikeCount = "0", publishedTime = "", isHeartedByUploader = false, isPinned = false,
        uploaderVerified = false, replyCount = 0, repliesPage = null,
    )

    private class Pages(private val pages: List<List<CommentItem>>, private val failAt: Int? = null) : CommentService {
        val requested = mutableListOf<String?>()
        override suspend fun getComments(url: String, nextpage: String?): ExtractionResult<CommentsPageResponse> {
            requested += nextpage
            val index = nextpage?.toInt() ?: 0
            if (index == failAt) return ExtractionResult.Failure("busy")
            val next = (index + 1).takeIf { it < pages.size }?.toString()
            return ExtractionResult.Success(CommentsPageResponse(pages[index], next, commentsDisabled = false))
        }
    }

    private fun query(text: String) = requireNotNull(CommentSearchQuery.parse(text))

    @Test
    fun `every word must be in the comment, in any case`() {
        val search = query("iPhone DUO")
        assertTrue(search.matches(comment("1", "the iphone duo is great")))
        assertFalse(search.matches(comment("2", "the iphone is great")))
    }

    @Test
    fun `quoted words are searched as a phrase`() {
        val search = query("\"iphone duo\" face")
        assertTrue(search.matches(comment("1", "face id on the iPhone Duo")))
        assertFalse(search.matches(comment("2", "duo iphone face")))
    }

    @Test
    fun `the author name is searched too`() {
        assertTrue(query("blender").matches(comment("1", "nice", author = "Blender Studio")))
    }

    @Test
    fun `turkish dotted and dotless i match a plain i`() {
        assertTrue(query("istanbul").matches(comment("1", "İSTANBUL")))
        assertTrue(query("kiz").matches(comment("2", "KIZ")))
        assertTrue(query("kiz").matches(comment("3", "kız")))
    }

    @Test
    fun `blank or oversized queries are refused`() {
        assertNull(CommentSearchQuery.parse("   "))
        assertNull(CommentSearchQuery.parse("a".repeat(CommentSearchQuery.MAX_LENGTH + 1)))
    }

    @Test
    fun `the walk stops at the page cap and returns where to continue`() = runBlocking {
        val pages = Pages(List(10) { page -> listOf(comment("$page", "page $page")) })
        val result = CommentSearchService(pages, maxPages = 3).search("video", query("missing"), null)
        val data = (result as ExtractionResult.Success).data
        assertEquals(3, data.scannedPages)
        assertEquals("3", data.nextpage)
        assertTrue(data.comments.isEmpty())
    }

    @Test
    fun `the walk stops once it has enough matches`() = runBlocking {
        val pages = Pages(List(10) { page -> listOf(comment("$page", "hello $page")) })
        val result = CommentSearchService(pages, enoughMatches = 2).search("video", query("hello"), null)
        val data = (result as ExtractionResult.Success).data
        assertEquals(listOf("0", "1"), data.comments.map { it.id })
        assertEquals("2", data.nextpage)
    }

    @Test
    fun `the walk ends with the last page and continues from a cursor`() = runBlocking {
        val pages = Pages(List(3) { page -> listOf(comment("$page", "hello")) })
        val data = (CommentSearchService(pages).search("video", query("hello"), "1") as ExtractionResult.Success).data
        assertEquals(listOf("1", "2"), data.comments.map { it.id })
        assertNull(data.nextpage)
        assertEquals(listOf<String?>("1", "2"), pages.requested)
    }

    @Test
    fun `a failure after the first page keeps the matches and the page to retry`() = runBlocking {
        val pages = Pages(List(5) { page -> listOf(comment("$page", "hello")) }, failAt = 2)
        val data = (CommentSearchService(pages).search("video", query("hello"), null) as ExtractionResult.Success).data
        assertEquals(listOf("0", "1"), data.comments.map { it.id })
        assertEquals("2", data.nextpage)
    }

    @Test
    fun `a failure on the first page is returned`() = runBlocking {
        val result = CommentSearchService(Pages(listOf(emptyList()), failAt = 0)).search("video", query("hello"), null)
        assertTrue(result is ExtractionResult.Failure)
    }
}
