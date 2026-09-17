package dev.typetype.server

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.SubscriptionsTable
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.services.SubscriptionGroupsService
import dev.typetype.server.services.SubscriptionGroupWriteResult
import dev.typetype.server.services.SubscriptionMembershipFilter
import dev.typetype.server.services.SubscriptionMembershipPageService
import dev.typetype.server.services.SubscriptionsService
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubscriptionMembershipPageServiceTest {
    private val pages = SubscriptionMembershipPageService()
    private val groups = SubscriptionGroupsService()
    private val subscriptions = SubscriptionsService()

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb(): Unit = TestDatabase.setup()
    }

    @BeforeEach
    fun clean(): Unit = TestDatabase.truncateAll()

    @Test
    fun `large libraries return only the requested stable page with account totals`() = runTest {
        DatabaseFactory.query {
            SubscriptionsTable.batchInsert(0..1000, shouldReturnGeneratedValues = false) { index ->
                this[SubscriptionsTable.userId] = TEST_USER_ID
                this[SubscriptionsTable.channelUrl] = url(index)
                this[SubscriptionsTable.name] = "Channel ${index.toString().padStart(4, '0')}"
                this[SubscriptionsTable.avatarUrl] = "avatar"
                this[SubscriptionsTable.subscribedAt] = 1L
            }
        }
        subscriptions.add("foreign", SubscriptionItem(url(0), "Foreign", "avatar"))
        val first = pages.getPage(TEST_USER_ID, SubscriptionMembershipFilter(limit = 7))
        val next = pages.getPage(TEST_USER_ID, SubscriptionMembershipFilter(page = 1, limit = 7))
        assertEquals(1001L, first.total)
        assertEquals(1001L, first.totalSubscriptions)
        assertEquals(1001L, first.ungroupedCount)
        assertEquals((0..6).map(::url), first.items.map { it.channelUrl })
        assertEquals((7..13).map(::url), next.items.map { it.channelUrl })
        assertTrue(pages.getPage(TEST_USER_ID, SubscriptionMembershipFilter(page = 999, limit = 7)).items.isEmpty())
        assertEquals(first, pages.getPage(TEST_USER_ID, SubscriptionMembershipFilter(limit = 7)))
    }

    @Test
    fun `membership filters intersect literal search while returning all groups on each row`() = runTest {
        for (index in 0..3) subscriptions.add(TEST_USER_ID, SubscriptionItem(url(index), "SAME $index", "avatar"))
        subscriptions.add(TEST_USER_ID, SubscriptionItem(url(4), "100%_\\done", "avatar"))
        val a = (groups.create(TEST_USER_ID, "A") as SubscriptionGroupWriteResult.Success).group
        val b = (groups.create(TEST_USER_ID, "B") as SubscriptionGroupWriteResult.Success).group
        groups.addSubscriptions(TEST_USER_ID, a.id, listOf(url(0), url(1)))
        groups.addSubscriptions(TEST_USER_ID, b.id, listOf(url(1), url(2)))
        val foreign = (groups.create("foreign", "Private") as SubscriptionGroupWriteResult.Success).group
        subscriptions.add("foreign", SubscriptionItem(url(3), "Foreign channel", "avatar"))
        groups.addSubscription("foreign", foreign.id, url(3))
        val inGroup = pages.getPage(TEST_USER_ID, SubscriptionMembershipFilter(groupId = a.id, search = "same"))
        assertEquals(listOf(url(0), url(1)), inGroup.items.map { it.channelUrl })
        assertEquals(listOf(a.id, b.id).sorted(), inGroup.items[1].groupIds)
        assertEquals(2L, inGroup.total)
        assertEquals(5L, inGroup.totalSubscriptions)
        assertEquals(2L, inGroup.ungroupedCount)
        assertEquals(listOf(url(2), url(3)), pages.getPage(TEST_USER_ID,
            SubscriptionMembershipFilter(groupId = a.id, excluded = true, search = " same ")).items.map { it.channelUrl })
        assertEquals(listOf(url(3)), pages.getPage(TEST_USER_ID,
            SubscriptionMembershipFilter(ungrouped = true, search = "SAME")).items.map { it.channelUrl })
        assertEquals(listOf(url(4)), pages.getPage(TEST_USER_ID,
            SubscriptionMembershipFilter(search = "%_\\")).items.map { it.channelUrl })
        assertEquals(listOf(url(2)), pages.getPage(TEST_USER_ID,
            SubscriptionMembershipFilter(search = url(2))).items.map { it.channelUrl })
    }

    @Test
    fun `lookup refreshes off-page selections and omits deleted or foreign subscriptions`() = runTest {
        subscriptions.add(TEST_USER_ID, SubscriptionItem(url(1), "One", "avatar"))
        subscriptions.add(TEST_USER_ID, SubscriptionItem(url(2), "Two", "avatar"))
        subscriptions.add("foreign", SubscriptionItem(url(3), "Foreign", "avatar"))
        val group = (groups.create(TEST_USER_ID, "A") as SubscriptionGroupWriteResult.Success).group
        groups.addSubscription(TEST_USER_ID, group.id, url(2))
        subscriptions.delete(TEST_USER_ID, url(1))
        val selected = pages.lookup(TEST_USER_ID, listOf(url(1), url(2), url(2), url(3)))
        assertEquals(1, selected.size)
        assertEquals(url(2), selected.single().channelUrl)
        assertEquals(listOf(group.id), selected.single().groupIds)
        assertEquals(1, groups.getAll(TEST_USER_ID).single().channelCount)
    }

    @Test
    fun `Unicode names match exact search without treating wildcards as patterns`() = runTest {
        val names = listOf("École", "İstanbul", "ΟΣ", "İzmir_%\\'News")
        for ((index, name) in names.withIndex()) {
            subscriptions.add(TEST_USER_ID, SubscriptionItem(url(index), name, "avatar"))
        }
        subscriptions.add(TEST_USER_ID, SubscriptionItem(url(99), "İzmirAXNews", "avatar"))
        for ((index, name) in names.withIndex()) {
            val page = pages.getPage(TEST_USER_ID, SubscriptionMembershipFilter(search = name))
            assertEquals(1L, page.total, name)
            assertEquals(listOf(url(index)), page.items.map { it.channelUrl }, name)
        }
        assertEquals("École", pages.getPage(TEST_USER_ID,
            SubscriptionMembershipFilter(search = "ÉCOLE")).items.single().name)
    }

    @Test
    fun `Unicode channel URLs use the same search normalization`() = runTest {
        val item = subscriptions.add(TEST_USER_ID,
            SubscriptionItem("https://example.com/İstanbul", "Ordinary channel", "avatar"))
        for (search in listOf("İstanbul", "istanbul")) {
            val page = pages.getPage(TEST_USER_ID, SubscriptionMembershipFilter(search = search))
            assertEquals(1L, page.total, search)
            assertEquals(item.channelUrl, page.items.single().channelUrl)
        }
    }

    private fun url(index: Int): String = "https://www.youtube.com/channel/test${index.toString().padStart(4, '0')}"
}
