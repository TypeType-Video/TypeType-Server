package dev.typetype.server

import dev.typetype.server.models.FocusSettingsRequest
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.services.FocusAllowOnceChallengeResult
import dev.typetype.server.services.FocusAllowOnceService
import dev.typetype.server.services.FocusAllowOnceVerifyResult
import dev.typetype.server.services.FocusService
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
import java.time.ZonedDateTime

class FocusAllowOnceServiceTest {
    private var now = ZonedDateTime.parse("2026-11-21T23:30:00+08:00[Asia/Shanghai]").toInstant().toEpochMilli()
    private var nextCode = "AB23CD"
    private val cache = FakeCacheService()
    private val focus = FocusService(clock = { now }, cache = cache, captchaCodeGenerator = { nextCode })
    private val groups = SubscriptionGroupsService()
    private val subscriptions = SubscriptionsService()

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        cache.clear()
        nextCode = "AB23CD"
    }

    @Test
    fun `allow once requires active strict focus and valid single-use captcha matching video`() = runTest {
        val group = setupEntertainmentGroup()
        assertEquals(
            FocusAllowOnceChallengeResult.NotEligible,
            focus.createAllowOnceChallenge(TEST_USER_ID, "https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
        )

        focus.update(TEST_USER_ID, FocusSettingsRequest(enabled = true, entertainmentGroupId = group, strictBlocking = true))
        val video1 = "https://www.youtube.com/watch?v=vidAllow001"
        val wrong = focus.createAllowOnceChallenge(TEST_USER_ID, video1, "Asia/Shanghai") as FocusAllowOnceChallengeResult.Success
        assertEquals(3, wrong.response.remainingToday)
        assertEquals("AB23CD", wrong.response.challengeText)

        assertEquals(
            FocusAllowOnceVerifyResult.InvalidChallenge,
            focus.verifyAllowOnceChallenge(TEST_USER_ID, wrong.response.challengeId, video1, "ab23cd", "Asia/Shanghai"),
        )
        assertEquals(
            FocusAllowOnceVerifyResult.InvalidChallenge,
            focus.verifyAllowOnceChallenge(TEST_USER_ID, wrong.response.challengeId, video1, "AB23CD", "Asia/Shanghai"),
        )

        val second = focus.createAllowOnceChallenge(TEST_USER_ID, video1, "Asia/Shanghai") as FocusAllowOnceChallengeResult.Success
        assertEquals(
            FocusAllowOnceVerifyResult.InvalidChallenge,
            focus.verifyAllowOnceChallenge(TEST_USER_ID, second.response.challengeId, "https://www.youtube.com/watch?v=other000001", "AB23CD"),
        )

        val third = focus.createAllowOnceChallenge(TEST_USER_ID, video1, "Asia/Shanghai") as FocusAllowOnceChallengeResult.Success
        val verified = focus.verifyAllowOnceChallenge(
            TEST_USER_ID,
            third.response.challengeId,
            "https://youtu.be/vidAllow001",
            "AB23CD",
            "Asia/Shanghai",
        ) as FocusAllowOnceVerifyResult.Success
        assertEquals(2, verified.response.remainingToday)
        assertEquals(2, focus.get(TEST_USER_ID, "Asia/Shanghai").allowOnceRemainingToday)

        val profile = requireNotNull(focus.profileFor(TEST_USER_ID))
        assertNull(profile.blockReasonForPlayback(video1, "https://www.youtube.com/channel/UCother", now))
        assertFalse(profile.isTemporaryAllowed("https://www.youtube.com/watch?v=vidOther002"))

        val repeatChallenge = focus.createAllowOnceChallenge(TEST_USER_ID, video1, "Asia/Shanghai") as FocusAllowOnceChallengeResult.Success
        val repeatVerify = focus.verifyAllowOnceChallenge(
            TEST_USER_ID,
            repeatChallenge.response.challengeId,
            video1,
            "AB23CD",
            "Asia/Shanghai",
        ) as FocusAllowOnceVerifyResult.Success
        assertEquals(2, repeatVerify.response.remainingToday)
    }

    @Test
    fun `daily allow once quota blocks once exhausted, resets at midnight, and expires after ttl`() = runTest {
        val group = setupEntertainmentGroup()
        focus.update(TEST_USER_ID, FocusSettingsRequest(enabled = true, entertainmentGroupId = group, strictBlocking = true))

        repeat(3) { idx ->
            val videoUrl = "https://www.youtube.com/watch?v=vidLimit00$idx"
            val challenge = focus.createAllowOnceChallenge(TEST_USER_ID, videoUrl, "Asia/Shanghai") as FocusAllowOnceChallengeResult.Success
            val result = focus.verifyAllowOnceChallenge(TEST_USER_ID, challenge.response.challengeId, videoUrl, "AB23CD", "Asia/Shanghai") as FocusAllowOnceVerifyResult.Success
            assertEquals(2 - idx, result.response.remainingToday)
        }

        assertEquals(0, focus.get(TEST_USER_ID, "Asia/Shanghai").allowOnceRemainingToday)
        assertEquals(
            FocusAllowOnceChallengeResult.Exhausted,
            focus.createAllowOnceChallenge(TEST_USER_ID, "https://www.youtube.com/watch?v=vidLimit009", "Asia/Shanghai"),
        )

        now += FocusAllowOnceService.ALLOW_ONCE_DURATION_MS + 1
        val afterExpiry = requireNotNull(focus.profileFor(TEST_USER_ID))
        assertFalse(afterExpiry.isTemporaryAllowed("https://www.youtube.com/watch?v=vidLimit000"))
        assertEquals(3, focus.get(TEST_USER_ID, "Asia/Shanghai").allowOnceRemainingToday)
        assertTrue(
            focus.createAllowOnceChallenge(TEST_USER_ID, "https://www.youtube.com/watch?v=vidNextDay1", "Asia/Shanghai")
                is FocusAllowOnceChallengeResult.Success,
        )
    }

    private suspend fun setupEntertainmentGroup(): String {
        val funChannel = "https://www.youtube.com/channel/UCfun"
        subscriptions.add(TEST_USER_ID, SubscriptionItem(channelUrl = funChannel, name = "Fun", avatarUrl = ""))
        val group = (groups.create(TEST_USER_ID, "Fun") as SubscriptionGroupWriteResult.Success).group.id
        assertEquals(SubscriptionGroupMembershipResult.Success, groups.addSubscription(TEST_USER_ID, group, funChannel))
        return group
    }
}
