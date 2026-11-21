package dev.typetype.server

import dev.typetype.server.models.FocusSettingsRequest
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.services.FocusChannelAccess
import dev.typetype.server.services.FocusProfile
import dev.typetype.server.services.FocusService
import dev.typetype.server.services.FocusStudyChannelRemoveResult
import dev.typetype.server.services.FocusStudyChannelResult
import dev.typetype.server.services.FocusUpdateResult
import dev.typetype.server.services.SubscriptionGroupMembershipResult
import dev.typetype.server.services.SubscriptionGroupWriteResult
import dev.typetype.server.services.SubscriptionGroupsService
import dev.typetype.server.services.SubscriptionsService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FocusServiceTest {
    private var now = 1_800_000_000_000L
    private val focus = FocusService(clock = { now })
    private val groups = SubscriptionGroupsService()
    private val subscriptions = SubscriptionsService()
    private val channel = "https://www.youtube.com/channel/UCfun"

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() = TestDatabase.truncateAll()

    @Test
    fun `enabling needs an existing group of the account`() = runTest {
        assertEquals(FocusUpdateResult.InvalidGroup, focus.update(TEST_USER_ID, FocusSettingsRequest(enabled = true)))
        assertEquals(
            FocusUpdateResult.InvalidGroup,
            focus.update(TEST_USER_ID, FocusSettingsRequest(enabled = true, entertainmentGroupId = "missing")),
        )
        val group = entertainmentGroup()
        assertEquals(
            FocusUpdateResult.InvalidDays,
            focus.update(TEST_USER_ID, FocusSettingsRequest(enabled = true, entertainmentGroupId = group, transitionDays = 0)),
        )
        val enabled = focus.update(TEST_USER_ID, FocusSettingsRequest(enabled = true, entertainmentGroupId = group))
        assertTrue((enabled as FocusUpdateResult.Success).item.enabled)
        assertEquals(now, enabled.item.startedAt)
    }

    @Test
    fun `turning it off waits a day and can be cancelled`() = runTest {
        val group = entertainmentGroup()
        focus.update(TEST_USER_ID, FocusSettingsRequest(enabled = true, entertainmentGroupId = group, transitionDays = 30))
        val pending = (focus.update(TEST_USER_ID, FocusSettingsRequest(enabled = false)) as FocusUpdateResult.Success).item
        assertTrue(pending.enabled)
        assertEquals(now + FocusService.EXIT_DELAY_MS, pending.disableAt)
        val loosened = focus.update(TEST_USER_ID, FocusSettingsRequest(enabled = true, transitionDays = 365))
        assertEquals(30, (loosened as FocusUpdateResult.Success).item.transitionDays)
        assertNull(loosened.item.disableAt)
        focus.update(TEST_USER_ID, FocusSettingsRequest(enabled = false))
        now += FocusService.EXIT_DELAY_MS - 1
        assertTrue(focus.get(TEST_USER_ID).enabled)
        now += 1
        assertFalse(focus.get(TEST_USER_ID).enabled)
        assertNull(focus.profileFor(TEST_USER_ID))
    }

    @Test
    fun `the profile follows the group and the transition`() = runTest {
        val group = entertainmentGroup()
        assertNull(focus.profileFor(TEST_USER_ID))
        focus.update(TEST_USER_ID, FocusSettingsRequest(enabled = true, entertainmentGroupId = group, transitionDays = 10, archiveDays = 3))
        now += 5 * FocusProfile.DAY_MS
        val profile = requireNotNull(focus.profileFor(TEST_USER_ID))
        assertTrue(profile.isEntertainment("https://youtube.com/channel/UCfun/"))
        assertEquals(0.5, profile.entertainmentShare, 1e-9)
        assertEquals(3, profile.archiveDays)
        assertEquals(0.5, focus.get(TEST_USER_ID).entertainmentShare, 1e-9)
        assertNull(focus.profileFor("other-user"))
    }

    @Test
    fun `study group and weekday schedule are checked and applied`() = runTest {
        val entertainment = entertainmentGroup()
        assertEquals(
            FocusUpdateResult.InvalidGroup,
            focus.update(TEST_USER_ID, FocusSettingsRequest(enabled = false, entertainmentGroupId = entertainment, studyGroupId = entertainment)),
        )
        assertEquals(
            FocusUpdateResult.InvalidSchedule,
            focus.update(TEST_USER_ID, FocusSettingsRequest(enabled = false, entertainmentGroupId = entertainment, schedule = "weekdays")),
        )
        val study = (groups.create(TEST_USER_ID, "Study") as SubscriptionGroupWriteResult.Success).group.id
        subscriptions.add(TEST_USER_ID, SubscriptionItem(channelUrl = "https://www.youtube.com/channel/UCstudy", name = "Study", avatarUrl = ""))
        groups.addSubscription(TEST_USER_ID, study, "https://www.youtube.com/channel/UCstudy")
        val saved = focus.update(
            TEST_USER_ID,
            FocusSettingsRequest(enabled = true, entertainmentGroupId = entertainment, studyGroupId = study, schedule = "weekdays", timezone = "Europe/Paris"),
        ) as FocusUpdateResult.Success
        assertEquals("weekdays", saved.item.schedule)
        now = java.time.ZonedDateTime.parse("2026-11-16T12:00:00+01:00[Europe/Paris]").toInstant().toEpochMilli()
        assertTrue(requireNotNull(focus.profileFor(TEST_USER_ID)).isStudy("https://youtube.com/channel/UCstudy"))
        now = java.time.ZonedDateTime.parse("2026-11-15T12:00:00+01:00[Europe/Paris]").toInstant().toEpochMilli()
        assertNull(focus.profileFor(TEST_USER_ID))
    }

    @Test
    fun `study channels added during focus cool off for 24 hours and cannot overlap entertainment`() = runTest {
        val entertainment = entertainmentGroup()
        val study = (groups.create(TEST_USER_ID, "Study") as SubscriptionGroupWriteResult.Success).group.id
        focus.update(
            TEST_USER_ID,
            FocusSettingsRequest(enabled = true, entertainmentGroupId = entertainment, studyGroupId = study, grayscaleThumbnails = false),
        )
        assertFalse(focus.get(TEST_USER_ID).grayscaleThumbnails)
        assertEquals(
            FocusStudyChannelResult.EntertainmentConflict,
            focus.addStudyChannel(TEST_USER_ID, channel, "Fun", ""),
        )
        assertEquals(
            SubscriptionGroupMembershipResult.FocusEntertainmentConflict,
            groups.addSubscription(TEST_USER_ID, study, channel),
        )
        val newStudy = "https://www.youtube.com/channel/UCnewstudy"
        now += 1_000
        val added = focus.addStudyChannel(TEST_USER_ID, newStudy, "New Study", "") as FocusStudyChannelResult.Success
        assertEquals("pending", added.response.status)
        assertEquals(now + FocusService.STUDY_ADD_DELAY_MS, added.response.unlockAt)
        assertTrue(subscriptions.getAll(TEST_USER_ID).any { it.channelUrl == newStudy })
        val pendingProfile = requireNotNull(focus.profileFor(TEST_USER_ID))
        assertEquals(FocusChannelAccess.PENDING_STUDY, pendingProfile.accessFor(newStudy))
        assertEquals(FocusStudyChannelRemoveResult.Success, focus.removeStudyChannel(TEST_USER_ID, newStudy))
        assertFalse(subscriptions.getAll(TEST_USER_ID).any { it.channelUrl == newStudy })
        assertEquals(FocusChannelAccess.CHALLENGE_REQUIRED, requireNotNull(focus.profileFor(TEST_USER_ID)).accessFor(newStudy))
        assertEquals(FocusStudyChannelRemoveResult.NotFound, focus.removeStudyChannel(TEST_USER_ID, newStudy))

        val preSubscribed = "https://www.youtube.com/channel/UCexisting"
        subscriptions.add(TEST_USER_ID, SubscriptionItem(channelUrl = preSubscribed, name = "Existing", avatarUrl = ""))
        focus.addStudyChannel(TEST_USER_ID, preSubscribed, "Existing", "")
        assertEquals(FocusStudyChannelRemoveResult.Success, focus.removeStudyChannel(TEST_USER_ID, preSubscribed))
        assertTrue(subscriptions.getAll(TEST_USER_ID).any { it.channelUrl == preSubscribed })

        focus.addStudyChannel(TEST_USER_ID, newStudy, "New Study", "")
        now += FocusService.STUDY_ADD_DELAY_MS
        val activeProfile = requireNotNull(focus.profileFor(TEST_USER_ID))
        assertEquals(FocusChannelAccess.ALLOW_STUDY, activeProfile.accessFor(newStudy))
    }

    private suspend fun entertainmentGroup(): String {
        subscriptions.add(TEST_USER_ID, SubscriptionItem(channelUrl = channel, name = "Fun", avatarUrl = ""))
        val group = (groups.create(TEST_USER_ID, "Fun") as SubscriptionGroupWriteResult.Success).group.id
        assertEquals(SubscriptionGroupMembershipResult.Success, groups.addSubscription(TEST_USER_ID, group, channel))
        return group
    }
}
