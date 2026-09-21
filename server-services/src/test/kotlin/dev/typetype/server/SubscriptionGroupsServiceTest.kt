package dev.typetype.server

import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.models.TypeTypeBackupItem
import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.services.SubscriptionGroupMembershipCleaner
import dev.typetype.server.services.SubscriptionGroupMembershipResult
import dev.typetype.server.services.SubscriptionGroupWriteResult
import dev.typetype.server.services.SubscriptionGroupsService
import dev.typetype.server.services.SubscriptionSelection
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.TypeTypeBackupCategory
import dev.typetype.server.services.TypeTypeBackupRestoreWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SubscriptionGroupsServiceTest {
    private val groups = SubscriptionGroupsService()
    private val subscriptions = SubscriptionsService()

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() = TestDatabase.truncateAll()

    @Test
    fun `group names are normalized unique and account scoped`() = runTest {
        val group = groups.create("user-a", "  Work  ").createdGroup()

        assertEquals("Work", group.name)
        assertEquals(SubscriptionGroupWriteResult.DuplicateName, groups.create("user-a", "work"))
        assertTrue(groups.create("user-b", "work") is SubscriptionGroupWriteResult.Success)
        assertFalse(groups.exists("user-b", group.id))
        assertEquals(
            SubscriptionGroupWriteResult.NotFound,
            groups.rename("user-b", group.id, "Other"),
        )
        groups.create("user-a", "Other")
        assertEquals(SubscriptionGroupWriteResult.DuplicateName, groups.rename("user-a", group.id, "OTHER"))
    }

    @Test
    fun `a subscription can belong to multiple groups while ungrouped stays distinct`() = runTest {
        subscriptions.add("user", subscription("one"))
        subscriptions.add("user", subscription("two"))
        subscriptions.add("user", subscription("three"))
        val first = groups.create("user", "First").createdGroup()
        val second = groups.create("user", "Second").createdGroup()

        assertEquals(SubscriptionGroupMembershipResult.Success, groups.addSubscription("user", first.id, channel("one")))
        assertEquals(SubscriptionGroupMembershipResult.Success, groups.addSubscription("user", second.id, channel("one")))
        assertEquals(SubscriptionGroupMembershipResult.Success, groups.addSubscription("user", second.id, channel("two")))
        assertEquals(1, groups.getAll("user").first { it.id == first.id }.channelCount)

        assertEquals(
            listOf(channel("one")),
            subscriptions.getAll("user", SubscriptionSelection.Group(first.id)).map { it.channelUrl },
        )
        assertEquals(
            setOf(channel("one"), channel("two")),
            subscriptions.getAll("user", SubscriptionSelection.Group(second.id)).map { it.channelUrl }.toSet(),
        )
        assertEquals(
            listOf(channel("three")),
            subscriptions.getAll("user", SubscriptionSelection.Ungrouped).map { it.channelUrl },
        )
    }

    @Test
    fun `membership requires both the users group and subscription`() = runTest {
        val group = groups.create("user-a", "A").createdGroup()
        subscriptions.add("user-b", subscription("shared"))

        assertEquals(
            SubscriptionGroupMembershipResult.SubscriptionNotFound,
            groups.addSubscription("user-a", group.id, channel("shared")),
        )
        assertEquals(
            SubscriptionGroupMembershipResult.GroupNotFound,
            groups.addSubscription("user-b", group.id, channel("shared")),
        )
    }

    @Test
    fun `deleting a subscription removes its memberships`() = runTest {
        val group = groups.create("user", "Group").createdGroup()
        subscriptions.add("user", subscription("one"))
        groups.addSubscription("user", group.id, channel("one"))

        assertTrue(subscriptions.delete("user", channel("one")))

        assertEquals(emptyList<String>(), groups.getChannelUrls("user", group.id))
    }

    @Test
    fun `membership assignment deletion and replacement share a user lock`() = runTest {
        val userId = "concurrent-user"
        val group = groups.create(userId, "Group").createdGroup()
        subscriptions.add(userId, subscription("one"))
        val lockHeld = CountDownLatch(1)
        val releaseLock = CountDownLatch(1)
        val holder = async(Dispatchers.IO) {
            DatabaseFactory.query {
                TransactionManager.current().exec(subscriptionLockSql(userId))
                lockHeld.countDown()
                check(releaseLock.await(5, TimeUnit.SECONDS))
            }
        }
        assertTrue(lockHeld.await(5, TimeUnit.SECONDS))

        val assignment = async(Dispatchers.IO) {
            groups.addSubscription(userId, group.id, channel("one"))
        }
        val deletion = async(Dispatchers.IO) { subscriptions.delete(userId, channel("one")) }
        val replacement = async(Dispatchers.IO) {
            TypeTypeBackupRestoreWriter.restore(
                userId = userId,
                backup = TypeTypeBackupItem(
                    exportedAt = 1,
                    categories = listOf(TypeTypeBackupCategory.SUBSCRIPTIONS.wireName),
                    subscriptions = listOf(subscription("one").copy(subscribedAt = 1)),
                ),
                categories = setOf(TypeTypeBackupCategory.SUBSCRIPTIONS),
            )
        }
        val allWaited = try {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(2_000L) {
                    var waiting = false
                    while (!waiting && !(assignment.isCompleted && deletion.isCompleted && replacement.isCompleted)) {
                        waiting = waitingSubscriptionLocks(userId) >= 3
                        if (!waiting) yield()
                    }
                    waiting
                } ?: false
            }
        } finally {
            releaseLock.countDown()
        }

        holder.await()
        assignment.await()
        assertTrue(deletion.await())
        replacement.await()
        assertTrue(allWaited, "all mutations must wait for the same account-scoped lock")
        val subscriptionUrls = subscriptions.getAll(userId).mapTo(hashSetOf(), SubscriptionItem::channelUrl)
        assertTrue(groups.getChannelUrls(userId, group.id).all { it in subscriptionUrls })
    }

    @Test
    fun `group mutations share the account subscription lock`() = runTest {
        val userId = "concurrent-group-user"
        subscriptions.add(userId, subscription("one"))
        val renamedGroup = groups.create(userId, "Rename").createdGroup()
        val deletedGroup = groups.create(userId, "Delete").createdGroup()
        val membershipGroup = groups.create(userId, "Membership").createdGroup()
        groups.addSubscription(userId, membershipGroup.id, channel("one"))
        val lockHeld = CountDownLatch(1)
        val releaseLock = CountDownLatch(1)
        val holder = async(Dispatchers.IO) {
            DatabaseFactory.query {
                TransactionManager.current().exec(subscriptionLockSql(userId))
                lockHeld.countDown()
                check(releaseLock.await(5, TimeUnit.SECONDS))
            }
        }
        assertTrue(lockHeld.await(5, TimeUnit.SECONDS))

        val creation = async(Dispatchers.IO) { groups.create(userId, "Created") }
        val rename = async(Dispatchers.IO) { groups.rename(userId, renamedGroup.id, "Renamed") }
        val deletion = async(Dispatchers.IO) { groups.delete(userId, deletedGroup.id) }
        val removal = async(Dispatchers.IO) {
            groups.removeSubscription(userId, membershipGroup.id, channel("one"))
        }
        val allWaited = try {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(2_000L) {
                    var waiting = false
                    while (!waiting && !(creation.isCompleted && rename.isCompleted && deletion.isCompleted && removal.isCompleted)) {
                        waiting = waitingSubscriptionLocks(userId) >= 4
                        if (!waiting) yield()
                    }
                    waiting
                } ?: false
            }
        } finally {
            releaseLock.countDown()
        }

        holder.await()
        assertTrue(creation.await() is SubscriptionGroupWriteResult.Success)
        assertTrue(rename.await() is SubscriptionGroupWriteResult.Success)
        assertTrue(deletion.await())
        assertEquals(SubscriptionGroupMembershipResult.Success, removal.await())
        assertTrue(allWaited, "all group mutations must wait for the account-scoped lock")
    }

    @Test
    fun `membership projection shares the account subscription lock`() = runTest {
        val userId = "projection-user"
        val group = groups.create(userId, "Group").createdGroup()
        subscriptions.add(userId, subscription("one"))
        groups.addSubscription(userId, group.id, channel("one"))
        val lockHeld = CountDownLatch(1)
        val releaseLock = CountDownLatch(1)
        val holder = async(Dispatchers.IO) {
            DatabaseFactory.query {
                TransactionManager.current().exec(subscriptionLockSql(userId))
                lockHeld.countDown()
                check(releaseLock.await(5, TimeUnit.SECONDS))
            }
        }
        assertTrue(lockHeld.await(5, TimeUnit.SECONDS))

        val projection = async(Dispatchers.IO) { subscriptions.getAllWithGroupMemberships(userId) }
        val readWaited = try {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(2_000L) {
                    while (!projection.isCompleted && waitingSubscriptionLocks(userId) == 0) yield()
                    !projection.isCompleted
                } ?: false
            }
        } finally {
            releaseLock.countDown()
        }

        holder.await()
        assertTrue(readWaited, "the membership projection must wait for the account-scoped lock")
        assertEquals(listOf(group.id), projection.await().single().groupIds)
    }

    @Test
    fun `replacement imports retain only memberships for subscriptions still present`() = runTest {
        val group = groups.create("user", "Group").createdGroup()
        subscriptions.add("user", subscription("one"))
        subscriptions.add("user", subscription("two"))
        groups.addSubscription("user", group.id, channel("one"))
        groups.addSubscription("user", group.id, channel("two"))

        DatabaseFactory.query { SubscriptionGroupMembershipCleaner.retain("user", listOf(channel("one"))) }

        assertEquals(listOf(channel("one")), groups.getChannelUrls("user", group.id))
    }

    private fun SubscriptionGroupWriteResult.createdGroup() =
        (this as SubscriptionGroupWriteResult.Success).group

    private fun subscription(id: String) = SubscriptionItem(channel(id), id, "")

    private fun channel(id: String) = "https://yt.com/channel/$id"

    private fun subscriptionLockSql(userId: String): String =
        "SELECT pg_advisory_xact_lock($SUBSCRIPTION_LOCK_NAMESPACE, ${subscriptionLockKey(userId)})"

    private suspend fun waitingSubscriptionLocks(userId: String): Int = DatabaseFactory.query {
        TransactionManager.current().exec(
            """
            SELECT count(*)
            FROM pg_locks
            WHERE locktype = 'advisory'
              AND classid = $SUBSCRIPTION_LOCK_NAMESPACE
              AND objid = ${subscriptionLockKey(userId)}
              AND NOT granted
            """.trimIndent(),
        ) { result ->
            result.next()
            result.getInt(1)
        } ?: 0
    }

    private fun subscriptionLockKey(userId: String): Int = userId.hashCode() and Int.MAX_VALUE
}

// Precomputed PostgreSQL hashtext('subscriptions'); must match SubscriptionMutationLock.
private const val SUBSCRIPTION_LOCK_NAMESPACE = 720_815_616
