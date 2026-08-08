package dev.typetype.server

import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.services.SubscriptionGroupMembershipCleaner
import dev.typetype.server.services.SubscriptionGroupMembershipResult
import dev.typetype.server.services.SubscriptionGroupWriteResult
import dev.typetype.server.services.SubscriptionGroupsService
import dev.typetype.server.services.SubscriptionSelection
import dev.typetype.server.services.SubscriptionsService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
}
