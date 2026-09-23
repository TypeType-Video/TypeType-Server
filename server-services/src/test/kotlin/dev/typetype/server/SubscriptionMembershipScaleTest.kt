package dev.typetype.server

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.services.SubscriptionMembershipFilter
import dev.typetype.server.services.SubscriptionMembershipPageService
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

class SubscriptionMembershipScaleTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb(): Unit = TestDatabase.setup()
    }

    @BeforeEach
    fun clean(): Unit = TestDatabase.truncateAll()

    @Test
    fun `pages stay responsive with 25000 channels and 112500 memberships`() = runTest {
        seedLibrary()
        val pages = SubscriptionMembershipPageService()
        val filters = listOf(
            SubscriptionMembershipFilter(limit = 7) to 25_000L,
            SubscriptionMembershipFilter(limit = 7, ungrouped = true) to 2_500L,
            SubscriptionMembershipFilter(limit = 7, groupId = "scale-1", excluded = true) to 2_500L,
            SubscriptionMembershipFilter(limit = 7, groupId = "scale-1") to 22_500L,
            SubscriptionMembershipFilter(limit = 7, search = "Channel 1") to 10_000L,
        )
        for ((filter, total) in filters) {
            val (page, elapsed) = measureTimedValue { pages.getPage(TEST_USER_ID, filter) }
            assertTrue(elapsed < 3.seconds, "$filter page read took $elapsed")
            assertEquals(total, page.total)
            assertEquals(25_000L, page.totalSubscriptions)
            assertEquals(2_500L, page.ungroupedCount)
            assertEquals(7, page.items.size)
            assertEquals(7, page.items.map { it.channelUrl }.distinct().size)
            assertTrue(page.items.all { channel ->
                when {
                    filter.ungrouped || filter.excluded -> channel.groupIds.isEmpty()
                    filter.groupId != null -> channel.groupIds.size == 5
                    else -> true
                }
            })
        }
    }

    private suspend fun seedLibrary(): Unit = DatabaseFactory.query {
        // This membership set exceeds PostgreSQL's default NOT IN hash budget.
        val transaction = TransactionManager.current()
        transaction.exec("""
            INSERT INTO subscriptions (user_id, channel_url, name, avatar_url, subscribed_at)
            SELECT '$TEST_USER_ID', 'https://www.youtube.com/channel/UC' || lpad(n::text, 22, '0'),
                'Channel ' || lpad(n::text, 5, '0'), 'avatar', 1
            FROM generate_series(1, 25000) n
        """.trimIndent())
        transaction.exec("""
            INSERT INTO subscription_groups (id, user_id, name, normalized_name, created_at, updated_at)
            SELECT 'scale-' || n, '$TEST_USER_ID', 'Group ' || n, 'group ' || n, 1, 1
            FROM generate_series(1, 5) n
        """.trimIndent())
        transaction.exec("""
            INSERT INTO subscription_group_memberships (group_id, user_id, channel_url, added_at)
            SELECT g.id, s.user_id, s.channel_url, 1
            FROM subscriptions s JOIN subscription_groups g ON g.user_id = s.user_id
            WHERE s.user_id = '$TEST_USER_ID' AND right(s.channel_url, 1) <> '0'
        """.trimIndent())
        transaction.exec("ANALYZE subscriptions")
        transaction.exec("ANALYZE subscription_group_memberships")
    }
}
