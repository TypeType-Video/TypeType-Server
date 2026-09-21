package dev.typetype.server.db

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

object SettingsSchemaMigrations {
    fun apply() {
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS subtitles_enabled BOOLEAN NOT NULL DEFAULT false")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS notification_popups_enabled BOOLEAN NOT NULL DEFAULT true")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS default_playback_speed DOUBLE PRECISION NOT NULL DEFAULT 1.0")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS default_subtitle_language TEXT NOT NULL DEFAULT ''")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS default_audio_language TEXT NOT NULL DEFAULT ''")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS prefer_original_language BOOLEAN NOT NULL DEFAULT false")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS enable_high_quality_playback BOOLEAN NOT NULL DEFAULT false")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS sponsor_block_mode TEXT NOT NULL DEFAULT 'auto_skip'")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS sponsor_block_category_actions TEXT NOT NULL DEFAULT '{}'")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS sponsor_block_minimum_duration INTEGER NOT NULL DEFAULT 0")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS sponsor_block_show_current_segment BOOLEAN NOT NULL DEFAULT true")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS sponsor_block_show_chapters BOOLEAN NOT NULL DEFAULT false")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS sponsor_block_show_full_video_labels BOOLEAN NOT NULL DEFAULT true")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS sponsor_block_manual_skip_on_full_video BOOLEAN NOT NULL DEFAULT true")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS sponsor_block_skip_non_music_only_on_music_videos BOOLEAN NOT NULL DEFAULT false")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS sponsor_block_mute_instead_of_skip BOOLEAN NOT NULL DEFAULT false")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS hide_home_recommendations BOOLEAN NOT NULL DEFAULT false")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS hide_continue_watching BOOLEAN NOT NULL DEFAULT false")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS hide_related_videos BOOLEAN NOT NULL DEFAULT false")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS hide_comments BOOLEAN NOT NULL DEFAULT false")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS hide_shorts BOOLEAN NOT NULL DEFAULT false")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS hide_subscription_live_streams BOOLEAN NOT NULL DEFAULT false")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS hide_members_only_content BOOLEAN NOT NULL DEFAULT false")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS disable_watch_history BOOLEAN NOT NULL DEFAULT false")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS skip_playlist_autoplay_screen BOOLEAN NOT NULL DEFAULT false")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS autoplay_on_open BOOLEAN NOT NULL DEFAULT true")
        exec("UPDATE settings SET autoplay_on_open = autoplay")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS autoplay_countdown_seconds INTEGER NOT NULL DEFAULT 10")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS subscription_sync_interval INTEGER NOT NULL DEFAULT 0")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS default_landing_page TEXT NOT NULL DEFAULT 'home'")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS access_mode TEXT NOT NULL DEFAULT 'unrestricted'")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS access_mode_admin_managed BOOLEAN NOT NULL DEFAULT false")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS access_mode_admin_managed_at BIGINT NOT NULL DEFAULT 0")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS dearrow_enabled BOOLEAN NOT NULL DEFAULT false")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS dearrow_title_mode TEXT NOT NULL DEFAULT 'dearrow'")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS dearrow_thumbnail_mode TEXT NOT NULL DEFAULT 'dearrow_or_random'")
        exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS dearrow_trust_mode TEXT NOT NULL DEFAULT 'accepted'")
    }

    private fun exec(sql: String) {
        TransactionManager.current().exec(sql)
    }
}
