package dev.typetype.server.services

import dev.typetype.server.models.CommentItem
import dev.typetype.server.models.CommentSearchResponse
import dev.typetype.server.models.ExtractionResult
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class CommentSearchService(
    private val comments: CommentService,
    private val maxPages: Int = MAX_PAGES,
    private val enoughMatches: Int = ENOUGH_MATCHES,
    private val searches: Semaphore = Semaphore(MAX_CONCURRENT_SEARCHES),
) {
    suspend fun search(url: String, query: CommentSearchQuery, cursor: String?): ExtractionResult<CommentSearchResponse> =
        searches.withPermit { walk(url, query, cursor) }

    private suspend fun walk(url: String, query: CommentSearchQuery, cursor: String?): ExtractionResult<CommentSearchResponse> {
        val matches = mutableListOf<CommentItem>()
        var page = cursor
        var scanned = 0
        var disabled = false
        while (scanned < maxPages && matches.size < enoughMatches) {
            when (val result = comments.getComments(url, page)) {
                is ExtractionResult.Success -> {
                    scanned += 1
                    disabled = result.data.commentsDisabled
                    result.data.comments.filterTo(matches, query::matches)
                    page = result.data.nextpage
                    if (page == null) break
                }
                is ExtractionResult.BadRequest -> if (scanned == 0) return result else break
                is ExtractionResult.Failure -> if (scanned == 0) return result else break
            }
        }
        return ExtractionResult.Success(CommentSearchResponse(matches, page, scanned, disabled))
    }

    companion object {
        const val MAX_PAGES = 20
        const val ENOUGH_MATCHES = 20
        const val MAX_CONCURRENT_SEARCHES = 4
    }
}
