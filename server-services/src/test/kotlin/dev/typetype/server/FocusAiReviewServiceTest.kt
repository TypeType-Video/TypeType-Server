package dev.typetype.server

import dev.typetype.server.ai.AiCompletionRequest
import dev.typetype.server.ai.AiCompletionResult
import dev.typetype.server.ai.AiKit
import dev.typetype.server.models.FocusSettingsRequest
import dev.typetype.server.models.HistoryItem
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.services.FocusAiConfig
import dev.typetype.server.services.FocusAiReviewResult
import dev.typetype.server.services.FocusAllowOnceCancelResult
import dev.typetype.server.services.FocusAllowOnceChallengeResult
import dev.typetype.server.services.FocusAllowOnceKind
import dev.typetype.server.services.FocusAllowOnceService
import dev.typetype.server.services.FocusAllowOnceVerifyResult
import dev.typetype.server.services.FocusBlockReason
import dev.typetype.server.services.FocusService
import dev.typetype.server.services.HistoryService
import dev.typetype.server.services.SubscriptionGroupMembershipResult
import dev.typetype.server.services.SubscriptionGroupWriteResult
import dev.typetype.server.services.SubscriptionGroupsService
import dev.typetype.server.services.SubscriptionsService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

class FocusAiReviewServiceTest {
    private var now = ZonedDateTime.parse("2026-11-24T12:00:00+08:00[Asia/Shanghai]").toInstant().toEpochMilli()
    private val cache = FakeCacheService()
    private val groups = SubscriptionGroupsService()
    private val subscriptions = SubscriptionsService()
    private val history = HistoryService()
    private var nextAiResult: AiCompletionResult = AiCompletionResult.Success("""{"score":90}""", "test", "gemini")

    private val fakeAi = object : AiKit {
        override val enabled: Boolean = true
        override val providerId: String = "gemini"
        override suspend fun complete(request: AiCompletionRequest): AiCompletionResult = nextAiResult
    }

    private val focus = FocusService(
        clock = { now },
        cache = cache,
        captchaCodeGenerator = { "AB23CD" },
        aiKit = fakeAi,
        focusAiConfig = FocusAiConfig(enabled = true),
    )

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        cache.clear()
        nextAiResult = AiCompletionResult.Success("""{"score":90}""", "test", "gemini")
    }

    @Test
    fun `ai approval grants free playback without consuming daily allow-once quota`() = runTest {
        val group = setupEntertainmentGroup()
        focus.update(TEST_USER_ID, FocusSettingsRequest(enabled = true, entertainmentGroupId = group, strictBlocking = true))
        val url = "https://www.youtube.com/watch?v=vidAiPass1"
        val stream = testStreamResponse().copy(title = "Math tutorial", uploaderUrl = "https://www.youtube.com/channel/UCother")
        assertNotNull(focus.playbackBlockContext(TEST_USER_ID, url, stream))

        nextAiResult = AiCompletionResult.Success("""{"score":85}""", "test", "gemini")
        val review = focus.reviewWithAi(TEST_USER_ID, url, "Preparing for calculus exam", "Asia/Shanghai")
        assertTrue(review is FocusAiReviewResult.Approved)
        assertNull(focus.playbackBlockContext(TEST_USER_ID, url, stream))
        assertEquals(3, focus.get(TEST_USER_ID, "Asia/Shanghai").allowOnceRemainingToday)
    }

    @Test
    fun `ai rejection starts 1h cooldown and allow-once cooling-off can be cancelled for full quota refund`() = runTest {
        val group = setupEntertainmentGroup()
        focus.update(TEST_USER_ID, FocusSettingsRequest(enabled = true, entertainmentGroupId = group, strictBlocking = true))
        val url = "https://www.youtube.com/watch?v=vidRewatch1"
        history.add(
            TEST_USER_ID,
            HistoryItem(
                url = url,
                title = "Fun comedy",
                thumbnail = "",
                channelName = "Fun",
                channelUrl = "https://www.youtube.com/channel/UCfun",
                duration = 600L,
                progress = 590L,
                watchedAt = now - 3_600_000L,
            ),
        )
        val stream = testStreamResponse().copy(
            title = "Fun comedy",
            uploaderUrl = "https://www.youtube.com/channel/UCfun",
            duration = 600L,
            publishedAt = now - 86_400_000L,
        )
        val initialCtx = requireNotNull(focus.playbackBlockContext(TEST_USER_ID, url, stream))
        assertEquals(FocusBlockReason.EntertainmentRewatch, initialCtx.reason)
        assertEquals(FocusAllowOnceKind.ENTERTAINMENT_REWATCH, initialCtx.allowOnceKind)

        nextAiResult = AiCompletionResult.Success("""{"score":10}""", "test", "gemini")
        val rejected = focus.reviewWithAi(TEST_USER_ID, url, "Just bored", "Asia/Shanghai") as FocusAiReviewResult.Rejected
        assertEquals(60 * 60_000L, rejected.cooldownMs)

        val blockedAfterReject = requireNotNull(focus.playbackBlockContext(TEST_USER_ID, url, stream))
        assertTrue(blockedAfterReject.aiRejected)
        assertEquals(60 * 60_000L, blockedAfterReject.allowOnceCooldownMs)
        assertTrue(focus.reviewWithAi(TEST_USER_ID, url, "New excuse", "Asia/Shanghai") is FocusAiReviewResult.CooldownActive)

        val challenge = focus.createAllowOnceChallenge(
            TEST_USER_ID,
            url,
            "Asia/Shanghai",
            FocusAllowOnceKind.ENTERTAINMENT_REWATCH.wireName,
        ) as FocusAllowOnceChallengeResult.Success
        val verify = focus.verifyAllowOnceChallenge(
            TEST_USER_ID,
            challenge.response.challengeId,
            url,
            "AB23CD",
            "Asia/Shanghai",
            FocusAllowOnceKind.ENTERTAINMENT_REWATCH.wireName,
        ) as FocusAllowOnceVerifyResult.Success
        assertEquals(1, verify.response.remainingToday)
        assertEquals(now + 60 * 60_000L, verify.response.unlockAt)

        val coolingCtx = requireNotNull(focus.playbackBlockContext(TEST_USER_ID, url, stream))
        assertEquals(now + 60 * 60_000L, coolingCtx.allowOncePendingUnlockAt)

        val cancelled = focus.cancelPendingAllowOnce(TEST_USER_ID, url, "Asia/Shanghai") as FocusAllowOnceCancelResult.Success
        assertEquals(2, cancelled.response.remainingToday)
        assertEquals(2, focus.get(TEST_USER_ID, "Asia/Shanghai").rewatchAllowOnceRemainingToday)
        val afterCancelCtx = requireNotNull(focus.playbackBlockContext(TEST_USER_ID, url, stream))
        assertNull(afterCancelCtx.allowOncePendingUnlockAt)
        assertTrue(afterCancelCtx.aiRejected)
    }

    @Test
    fun `ai unavailable returns rest state without writing rejection cooldown`() = runTest {
        val group = setupEntertainmentGroup()
        focus.update(TEST_USER_ID, FocusSettingsRequest(enabled = true, entertainmentGroupId = group, strictBlocking = true))
        val url = "https://www.youtube.com/watch?v=vidRest1"
        val stream = testStreamResponse().copy(title = "Study video", uploaderUrl = "https://www.youtube.com/channel/UCother")
        assertNotNull(focus.playbackBlockContext(TEST_USER_ID, url, stream))

        nextAiResult = AiCompletionResult.RateLimited("429")
        assertEquals(FocusAiReviewResult.Unavailable, focus.reviewWithAi(TEST_USER_ID, url, "Study", "Asia/Shanghai"))
        val ctx = requireNotNull(focus.playbackBlockContext(TEST_USER_ID, url, stream))
        assertFalse(ctx.aiRejected)
        assertNull(ctx.allowOnceCooldownMs)
    }

    private suspend fun setupEntertainmentGroup(): String {
        val funChannel = "https://www.youtube.com/channel/UCfun"
        subscriptions.add(TEST_USER_ID, SubscriptionItem(channelUrl = funChannel, name = "Fun", avatarUrl = ""))
        val group = (groups.create(TEST_USER_ID, "Fun") as SubscriptionGroupWriteResult.Success).group.id
        assertEquals(SubscriptionGroupMembershipResult.Success, groups.addSubscription(TEST_USER_ID, group, funChannel))
        return group
    }
}
