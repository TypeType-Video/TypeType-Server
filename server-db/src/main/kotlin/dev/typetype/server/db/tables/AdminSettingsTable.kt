package dev.typetype.server.db.tables

import dev.typetype.server.DEFAULT_INSTANCE_NAME
import org.jetbrains.exposed.v1.core.Table

object AdminSettingsTable : Table("admin_settings") {
    val id = integer("id").default(1)
    val name = text("name").default(DEFAULT_INSTANCE_NAME)
    val tagline = text("tagline").nullable()
    val logoUrl = text("logo_url").nullable()
    val bannerUrl = text("banner_url").nullable()
    val minAndroidClientVersion = text("min_android_client_version").nullable()
    val allowRegistration = bool("allow_registration").default(true)
    val allowGuest = bool("allow_guest").default(true)
    val forceEmailVerification = bool("force_email_verification").default(false)
    val activeSessionsEnabled = bool("active_sessions_enabled").default(false)
    val localLoginEnabled = bool("local_login_enabled").default(true)
    val oidcAutoRedirect = bool("oidc_auto_redirect").default(false)
    val youtubeRemoteLoginEnabled = bool("youtube_remote_login_enabled").default(false)
    val accessMode = text("access_mode").default("unrestricted")
    val rssEnabled = bool("rss_enabled").default(false)
    val rssPublicBaseUrl = text("rss_public_base_url").nullable()
    val rssMaxFeedsPerUser = integer("rss_max_feeds_per_user").default(10)
    val rssMaxItems = integer("rss_max_items").default(50)
    val rssMinimumPollMinutes = integer("rss_minimum_poll_minutes").default(5)
    val rssRateLimitPerMinute = integer("rss_rate_limit_per_minute").default(30)
    override val primaryKey = PrimaryKey(id)
}
