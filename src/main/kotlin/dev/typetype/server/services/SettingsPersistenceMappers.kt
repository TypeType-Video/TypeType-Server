package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.db.tables.SettingsTable
import dev.typetype.server.models.CaptionStylesItem
import dev.typetype.server.models.DEFAULT_SPONSOR_BLOCK_CATEGORY_ACTIONS
import dev.typetype.server.models.SettingsItem
import dev.typetype.server.models.SponsorBlockMode
import dev.typetype.server.models.toSponsorBlockMode
import dev.typetype.server.models.withDefaultSponsorBlockCategoryActions
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

private val SPONSOR_BLOCK_CATEGORY_ACTIONS_SERIALIZER = MapSerializer(String.serializer(), SponsorBlockMode.serializer())
private val CAPTION_STYLES_SERIALIZER = CaptionStylesItem.serializer()

internal fun ResultRow.toSettingsItem(): SettingsItem = SettingsItem(
    defaultService = this[SettingsTable.defaultService],
    defaultQuality = this[SettingsTable.defaultQuality],
    defaultPlaybackSpeed = this[SettingsTable.defaultPlaybackSpeed],
    defaultLandingPage = this[SettingsTable.defaultLandingPage],
    autoplay = this[SettingsTable.autoplay],
    autoplayOnOpen = this[SettingsTable.autoplayOnOpen],
    autoplayCountdownSeconds = this[SettingsTable.autoplayCountdownSeconds],
    skipPlaylistAutoplayScreen = this[SettingsTable.skipPlaylistAutoplayScreen],
    volume = this[SettingsTable.volume],
    muted = this[SettingsTable.muted],
    notificationPopupsEnabled = this[SettingsTable.notificationPopupsEnabled],
    subtitlesEnabled = this[SettingsTable.subtitlesEnabled],
    defaultSubtitleLanguage = this[SettingsTable.defaultSubtitleLanguage],
    defaultAudioLanguage = this[SettingsTable.defaultAudioLanguage],
    captionStyles = decodeCaptionStyles(this[SettingsTable.captionStyles]),
    preferOriginalLanguage = this[SettingsTable.preferOriginalLanguage],
    enableHighQualityPlayback = this[SettingsTable.enableHighQualityPlayback],
    sponsorBlockMode = this[SettingsTable.sponsorBlockMode].toSponsorBlockMode(),
    sponsorBlockCategoryActions = decodeSponsorBlockCategoryActions(this[SettingsTable.sponsorBlockCategoryActions]),
    sponsorBlockMinimumDuration = this[SettingsTable.sponsorBlockMinimumDuration],
    sponsorBlockShowCurrentSegment = this[SettingsTable.sponsorBlockShowCurrentSegment],
    sponsorBlockShowChapters = this[SettingsTable.sponsorBlockShowChapters],
    sponsorBlockShowFullVideoLabels = this[SettingsTable.sponsorBlockShowFullVideoLabels],
    sponsorBlockManualSkipOnFullVideo = this[SettingsTable.sponsorBlockManualSkipOnFullVideo],
    sponsorBlockSkipNonMusicOnlyOnMusicVideos = this[SettingsTable.sponsorBlockSkipNonMusicOnlyOnMusicVideos],
    sponsorBlockMuteInsteadOfSkip = this[SettingsTable.sponsorBlockMuteInsteadOfSkip],
    hideHomeRecommendations = this[SettingsTable.hideHomeRecommendations],
    hideContinueWatching = this[SettingsTable.hideContinueWatching],
    hideRelatedVideos = this[SettingsTable.hideRelatedVideos],
    hideComments = this[SettingsTable.hideComments],
    hideShorts = this[SettingsTable.hideShorts],
    hideSubscriptionLiveStreams = this[SettingsTable.hideSubscriptionLiveStreams],
    hideMembersOnlyContent = this[SettingsTable.hideMembersOnlyContent],
    disableWatchHistory = this[SettingsTable.disableWatchHistory],
    deArrowEnabled = this[SettingsTable.deArrowEnabled],
    deArrowTitleMode = this[SettingsTable.deArrowTitleMode],
    deArrowThumbnailMode = this[SettingsTable.deArrowThumbnailMode],
    deArrowTrustMode = this[SettingsTable.deArrowTrustMode],
    accessMode = this[SettingsTable.accessMode].toAccessMode(),
)

internal fun UpdateBuilder<*>.writeSettings(settings: SettingsItem) {
    this[SettingsTable.defaultService] = settings.defaultService
    this[SettingsTable.defaultQuality] = settings.defaultQuality
    this[SettingsTable.defaultPlaybackSpeed] = settings.defaultPlaybackSpeed
    this[SettingsTable.defaultLandingPage] = settings.defaultLandingPage
    this[SettingsTable.autoplay] = settings.autoplay
    this[SettingsTable.autoplayOnOpen] = settings.autoplayOnOpen
    this[SettingsTable.autoplayCountdownSeconds] = settings.autoplayCountdownSeconds
    this[SettingsTable.skipPlaylistAutoplayScreen] = settings.skipPlaylistAutoplayScreen
    this[SettingsTable.volume] = settings.volume
    this[SettingsTable.muted] = settings.muted
    this[SettingsTable.notificationPopupsEnabled] = settings.notificationPopupsEnabled
    this[SettingsTable.subtitlesEnabled] = settings.subtitlesEnabled
    this[SettingsTable.defaultSubtitleLanguage] = settings.defaultSubtitleLanguage
    this[SettingsTable.defaultAudioLanguage] = settings.defaultAudioLanguage
    this[SettingsTable.captionStyles] = encodeCaptionStyles(settings.captionStyles)
    this[SettingsTable.preferOriginalLanguage] = settings.preferOriginalLanguage
    this[SettingsTable.enableHighQualityPlayback] = settings.enableHighQualityPlayback
    this[SettingsTable.sponsorBlockMode] = settings.sponsorBlockMode.storageValue
    this[SettingsTable.sponsorBlockCategoryActions] = encodeSponsorBlockCategoryActions(settings.sponsorBlockCategoryActions)
    this[SettingsTable.sponsorBlockMinimumDuration] = settings.sponsorBlockMinimumDuration
    this[SettingsTable.sponsorBlockShowCurrentSegment] = settings.sponsorBlockShowCurrentSegment
    this[SettingsTable.sponsorBlockShowChapters] = settings.sponsorBlockShowChapters
    this[SettingsTable.sponsorBlockShowFullVideoLabels] = settings.sponsorBlockShowFullVideoLabels
    this[SettingsTable.sponsorBlockManualSkipOnFullVideo] = settings.sponsorBlockManualSkipOnFullVideo
    this[SettingsTable.sponsorBlockSkipNonMusicOnlyOnMusicVideos] = settings.sponsorBlockSkipNonMusicOnlyOnMusicVideos
    this[SettingsTable.sponsorBlockMuteInsteadOfSkip] = settings.sponsorBlockMuteInsteadOfSkip
    this[SettingsTable.hideHomeRecommendations] = settings.hideHomeRecommendations
    this[SettingsTable.hideContinueWatching] = settings.hideContinueWatching
    this[SettingsTable.hideRelatedVideos] = settings.hideRelatedVideos
    this[SettingsTable.hideComments] = settings.hideComments
    this[SettingsTable.hideShorts] = settings.hideShorts
    this[SettingsTable.hideSubscriptionLiveStreams] = settings.hideSubscriptionLiveStreams
    this[SettingsTable.hideMembersOnlyContent] = settings.hideMembersOnlyContent
    this[SettingsTable.disableWatchHistory] = settings.disableWatchHistory
    this[SettingsTable.deArrowEnabled] = settings.deArrowEnabled
    this[SettingsTable.deArrowTitleMode] = settings.deArrowTitleMode
    this[SettingsTable.deArrowThumbnailMode] = settings.deArrowThumbnailMode
    this[SettingsTable.deArrowTrustMode] = settings.deArrowTrustMode
    this[SettingsTable.accessMode] = settings.accessMode.toAccessMode()
    this[SettingsTable.accessModeAdminManaged] = false
    this[SettingsTable.accessModeAdminManagedAt] = 0
}

internal fun SettingsItem.normalized(): SettingsItem = copy(
    defaultLandingPage = defaultLandingPage.ifBlank { "home" },
    defaultPlaybackSpeed = defaultPlaybackSpeed.takeIf { it.isFinite() }
        ?.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED) ?: 1.0,
    accessMode = accessMode.toAccessMode(),
    autoplayCountdownSeconds = autoplayCountdownSeconds.coerceIn(0, 60),
    sponsorBlockCategoryActions = sponsorBlockCategoryActions.withDefaultSponsorBlockCategoryActions(),
    sponsorBlockMinimumDuration = sponsorBlockMinimumDuration.coerceAtLeast(0),
    deArrowTitleMode = deArrowTitleMode.takeIf { it in DEARROW_TITLE_MODES } ?: "dearrow",
    deArrowThumbnailMode = deArrowThumbnailMode.takeIf { it in DEARROW_THUMBNAIL_MODES } ?: "dearrow_or_random",
    deArrowTrustMode = deArrowTrustMode.takeIf { it in DEARROW_TRUST_MODES } ?: "accepted",
)

private val DEARROW_TITLE_MODES = setOf("original", "dearrow")
private const val MIN_PLAYBACK_SPEED = 0.25
private const val MAX_PLAYBACK_SPEED = 4.0
private val DEARROW_THUMBNAIL_MODES = setOf("original", "dearrow", "random", "dearrow_or_random")
private val DEARROW_TRUST_MODES = setOf("accepted", "locked")

private fun decodeSponsorBlockCategoryActions(raw: String): Map<String, SponsorBlockMode> =
    runCatching { CacheJson.decodeFromString(SPONSOR_BLOCK_CATEGORY_ACTIONS_SERIALIZER, raw) }
        .getOrElse { DEFAULT_SPONSOR_BLOCK_CATEGORY_ACTIONS }
        .withDefaultSponsorBlockCategoryActions()

private fun encodeSponsorBlockCategoryActions(actions: Map<String, SponsorBlockMode>): String =
    CacheJson.encodeToString(SPONSOR_BLOCK_CATEGORY_ACTIONS_SERIALIZER, actions)

private fun decodeCaptionStyles(raw: String): CaptionStylesItem =
    runCatching { CacheJson.decodeFromString(CAPTION_STYLES_SERIALIZER, raw) }
        .getOrDefault(CaptionStylesItem())

private fun encodeCaptionStyles(styles: CaptionStylesItem): String =
    CacheJson.encodeToString(CAPTION_STYLES_SERIALIZER, styles)
