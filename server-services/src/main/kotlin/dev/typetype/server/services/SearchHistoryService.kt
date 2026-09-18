package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.SearchHistoryTable
import dev.typetype.server.models.SearchHistoryItem
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

class SearchHistoryService {

    suspend fun getAll(userId: String): List<SearchHistoryItem> = DatabaseFactory.query {
        SearchHistoryTable.selectAll()
            .where { SearchHistoryTable.userId eq userId }
            .orderBy(SearchHistoryTable.searchedAt to SortOrder.DESC)
            .map { it.toItem() }
    }

    suspend fun getPage(userId: String, page: Int, limit: Int): Pair<List<SearchHistoryItem>, Long> = DatabaseFactory.query {
        val query = SearchHistoryTable.selectAll()
            .where { SearchHistoryTable.userId eq userId }
            .orderBy(SearchHistoryTable.searchedAt to SortOrder.DESC)
        val total = query.count()
        val offset = (page - 1).toLong() * limit.toLong()
        val items = query.limit(limit).offset(offset).map { it.toItem() }
        items to total
    }

    suspend fun add(userId: String, term: String): SearchHistoryItem {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        DatabaseFactory.query {
            SearchHistoryTable.insert {
                it[SearchHistoryTable.id] = id
                it[SearchHistoryTable.userId] = userId
                it[SearchHistoryTable.term] = term
                it[searchedAt] = now
            }
        }
        return SearchHistoryItem(id = id, term = term, searchedAt = now)
    }

    suspend fun deleteAll(userId: String): Unit = DatabaseFactory.query {
        SearchHistoryTable.deleteWhere { SearchHistoryTable.userId eq userId }
    }

    private fun ResultRow.toItem() = SearchHistoryItem(
        id = this[SearchHistoryTable.id],
        term = this[SearchHistoryTable.term],
        searchedAt = this[SearchHistoryTable.searchedAt],
    )
}
