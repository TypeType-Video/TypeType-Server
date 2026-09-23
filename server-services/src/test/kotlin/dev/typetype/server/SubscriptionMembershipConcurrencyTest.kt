package dev.typetype.server

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.services.SubscriptionMembershipFilter
import dev.typetype.server.services.SubscriptionMembershipPageService
import dev.typetype.server.services.SubscriptionMutationLock
import dev.typetype.server.services.SubscriptionsService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SubscriptionMembershipConcurrencyTest {
    private val subscriptions = SubscriptionsService()
    private val pages = SubscriptionMembershipPageService()

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb(): Unit = TestDatabase.setup()
    }

    @BeforeEach
    fun clean(): Unit = TestDatabase.truncateAll()

    @Test
    fun `page reads and additions wait for the same account mutation`() = runTest {
        val lockHeld = CompletableDeferred<Int>()
        val releaseLock = CountDownLatch(1)
        val holder = async(Dispatchers.IO) {
            DatabaseFactory.query {
                SubscriptionMutationLock.acquire(TEST_USER_ID)
                val pid = TransactionManager.current().exec("SELECT pg_backend_pid()") { result ->
                    result.next()
                    result.getInt(1)
                }
                lockHeld.complete(requireNotNull(pid))
                check(releaseLock.await(10, TimeUnit.SECONDS))
            }
        }
        try {
            val holderPid = lockHeld.await()
            val addition = async(Dispatchers.IO) { subscriptions.add(TEST_USER_ID, subscription(0)) }
            val read = async(Dispatchers.IO) { pages.getPage(TEST_USER_ID, SubscriptionMembershipFilter()) }
            val otherAccount = withContext(Dispatchers.IO) {
                withTimeoutOrNull(5_000L) { subscriptions.add("other-account", subscription(0)) }
            }
            assertEquals(subscription(0).channelUrl, otherAccount?.channelUrl)
            val bothWaited = withContext(Dispatchers.IO) {
                withTimeoutOrNull(5_000L) {
                    while (!addition.isCompleted && !read.isCompleted && waitingTransactions(holderPid) < 2) {
                        delay(10)
                    }
                    waitingTransactions(holderPid) >= 2
                } ?: false
            }
            assertTrue(bothWaited, "page reads and additions must wait for the same account-scoped lock")
            releaseLock.countDown()
            holder.await()
            addition.await()
            val page = read.await()
            assertEquals(page.total, page.totalSubscriptions)
            assertEquals(page.total, page.ungroupedCount)
            assertEquals(page.total.toInt(), page.items.size)
            assertEquals(1L, pages.getPage(TEST_USER_ID, SubscriptionMembershipFilter()).total)
        } finally {
            releaseLock.countDown()
        }
    }

    @Test
    fun `page rows and counts remain consistent during concurrent additions`() = runTest {
        val start = CompletableDeferred<Unit>()
        val reader = async(Dispatchers.IO) {
            start.await()
            repeat(100) {
                val page = pages.getPage(TEST_USER_ID, SubscriptionMembershipFilter(limit = 20))
                assertEquals(page.total, page.totalSubscriptions)
                assertEquals(page.total, page.ungroupedCount)
                assertEquals(minOf(page.total, 20L).toInt(), page.items.size)
            }
        }
        val writer = async(Dispatchers.IO) {
            start.await()
            repeat(100) { subscriptions.add(TEST_USER_ID, subscription(it)) }
        }
        start.complete(Unit)
        reader.await()
        writer.await()
        assertEquals(100L, pages.getPage(TEST_USER_ID, SubscriptionMembershipFilter()).total)
    }

    private suspend fun waitingTransactions(holderPid: Int): Int = DatabaseFactory.query {
        TransactionManager.current().exec(
            "SELECT count(*) FROM pg_stat_activity WHERE $holderPid = ANY(pg_blocking_pids(pid))",
        ) { result ->
            result.next()
            result.getInt(1)
        } ?: 0
    }

    private fun subscription(index: Int): SubscriptionItem =
        SubscriptionItem("https://example.com/channel/$index", "Channel $index", "avatar")
}
