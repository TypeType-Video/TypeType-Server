package dev.typetype.server.services

import dev.typetype.server.db.tables.AllowedChannelsTable
import dev.typetype.server.db.tables.AllowedPlaylistsTable
import dev.typetype.server.db.tables.BlockedChannelsTable
import dev.typetype.server.db.tables.BlockedKeywordsTable
import dev.typetype.server.db.tables.BlockedVideosTable
import dev.typetype.server.db.tables.BugReportsTable
import dev.typetype.server.db.tables.FavoritesTable
import dev.typetype.server.db.tables.HistoryTable
import dev.typetype.server.db.tables.NotificationStatesTable
import dev.typetype.server.db.tables.PasswordResetTable
import dev.typetype.server.db.tables.PlaylistVideosTable
import dev.typetype.server.db.tables.PlaylistsTable
import dev.typetype.server.db.tables.ProgressTable
import dev.typetype.server.db.tables.RecommendationEventsTable
import dev.typetype.server.db.tables.RecommendationFeedbackTable
import dev.typetype.server.db.tables.RecommendationFeedHistoryTable
import dev.typetype.server.db.tables.RecommendationOnboardingPreferencesTable
import dev.typetype.server.db.tables.RecommendationOnboardingStateTable
import dev.typetype.server.db.tables.RssFeedChannelsTable
import dev.typetype.server.db.tables.RssFeedServicesTable
import dev.typetype.server.db.tables.RssFeedsTable
import dev.typetype.server.db.tables.RssUserPoliciesTable
import dev.typetype.server.db.tables.SavedPlaylistsTable
import dev.typetype.server.db.tables.SearchHistoryTable
import dev.typetype.server.db.tables.SessionsTable
import dev.typetype.server.db.tables.SettingsTable
import dev.typetype.server.db.tables.SubscriptionGroupMembershipsTable
import dev.typetype.server.db.tables.SubscriptionGroupsTable
import dev.typetype.server.db.tables.SubscriptionsTable
import dev.typetype.server.db.tables.UserAvatarsTable
import dev.typetype.server.db.tables.UserChannelInterestTable
import dev.typetype.server.db.tables.UserTopicInterestTable
import dev.typetype.server.db.tables.WatchLaterTable
import dev.typetype.server.db.tables.YoutubeSessionPairingsTable
import dev.typetype.server.db.tables.YoutubeSessionsTable
import dev.typetype.server.db.tables.YoutubeTakeoutImportJobsTable
import dev.typetype.server.db.tables.YoutubeTakeoutPlaylistKeysTable
import dev.typetype.server.db.tables.UsersTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll

internal object ProfileDataDeletionService {
    fun deleteUser(userId: String) {
        val feedIds = RssFeedsTable.selectAll().where { RssFeedsTable.userId eq userId }
            .map { it[RssFeedsTable.id] }
        feedIds.forEach { feedId ->
            RssFeedChannelsTable.deleteWhere { RssFeedChannelsTable.feedId eq feedId }
            RssFeedServicesTable.deleteWhere { RssFeedServicesTable.feedId eq feedId }
        }
        RssFeedsTable.deleteWhere { RssFeedsTable.userId eq userId }

        val groupIds = SubscriptionGroupsTable.selectAll().where { SubscriptionGroupsTable.userId eq userId }
            .map { it[SubscriptionGroupsTable.id] }
        SubscriptionGroupMembershipsTable.deleteWhere { SubscriptionGroupMembershipsTable.userId eq userId }
        groupIds.forEach { groupId ->
            SubscriptionGroupMembershipsTable.deleteWhere { SubscriptionGroupMembershipsTable.groupId eq groupId }
        }

        PlaylistVideosTable.deleteWhere { PlaylistVideosTable.userId eq userId }
        PlaylistsTable.deleteWhere { PlaylistsTable.userId eq userId }
        deleteUserRows(userId)
        UsersTable.deleteWhere { UsersTable.id eq userId }
    }

    private fun deleteUserRows(userId: String) {
        listOf(
            { HistoryTable.deleteWhere { HistoryTable.userId eq userId } },
            { FavoritesTable.deleteWhere { FavoritesTable.userId eq userId } },
            { ProgressTable.deleteWhere { ProgressTable.userId eq userId } },
            { WatchLaterTable.deleteWhere { WatchLaterTable.userId eq userId } },
            { SubscriptionsTable.deleteWhere { SubscriptionsTable.userId eq userId } },
            { SubscriptionGroupsTable.deleteWhere { SubscriptionGroupsTable.userId eq userId } },
            { SavedPlaylistsTable.deleteWhere { SavedPlaylistsTable.userId eq userId } },
            { SearchHistoryTable.deleteWhere { SearchHistoryTable.userId eq userId } },
            { SettingsTable.deleteWhere { SettingsTable.userId eq userId } },
            { AllowedChannelsTable.deleteWhere { AllowedChannelsTable.userId eq userId } },
            { AllowedPlaylistsTable.deleteWhere { AllowedPlaylistsTable.userId eq userId } },
            { BlockedChannelsTable.deleteWhere { BlockedChannelsTable.userId eq userId } },
            { BlockedKeywordsTable.deleteWhere { BlockedKeywordsTable.userId eq userId } },
            { BlockedVideosTable.deleteWhere { BlockedVideosTable.userId eq userId } },
            { NotificationStatesTable.deleteWhere { NotificationStatesTable.userId eq userId } },
            { PasswordResetTable.deleteWhere { PasswordResetTable.userId eq userId } },
            { SessionsTable.deleteWhere { SessionsTable.userId eq userId } },
            { UserAvatarsTable.deleteWhere { UserAvatarsTable.userId eq userId } },
            { UserChannelInterestTable.deleteWhere { UserChannelInterestTable.userId eq userId } },
            { UserTopicInterestTable.deleteWhere { UserTopicInterestTable.userId eq userId } },
            { YoutubeSessionsTable.deleteWhere { YoutubeSessionsTable.userId eq userId } },
            { YoutubeSessionPairingsTable.deleteWhere { YoutubeSessionPairingsTable.userId eq userId } },
            { YoutubeTakeoutImportJobsTable.deleteWhere { YoutubeTakeoutImportJobsTable.userId eq userId } },
            { YoutubeTakeoutPlaylistKeysTable.deleteWhere { YoutubeTakeoutPlaylistKeysTable.userId eq userId } },
            { RecommendationEventsTable.deleteWhere { RecommendationEventsTable.userId eq userId } },
            { RecommendationFeedHistoryTable.deleteWhere { RecommendationFeedHistoryTable.userId eq userId } },
            { RecommendationFeedbackTable.deleteWhere { RecommendationFeedbackTable.userId eq userId } },
            { RecommendationOnboardingPreferencesTable.deleteWhere { RecommendationOnboardingPreferencesTable.userId eq userId } },
            { RecommendationOnboardingStateTable.deleteWhere { RecommendationOnboardingStateTable.userId eq userId } },
            { RssUserPoliciesTable.deleteWhere { RssUserPoliciesTable.userId eq userId } },
        ).forEach { it() }
    }
}
