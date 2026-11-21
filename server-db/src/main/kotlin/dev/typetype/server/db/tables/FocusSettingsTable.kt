package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object FocusSettingsTable : Table("focus_settings") {
    val userId = text("user_id")
    val enabled = bool("enabled").default(false)
    val entertainmentGroupId = text("entertainment_group_id").nullable()
    val studyGroupId = text("study_group_id").nullable()
    val schedule = text("schedule").default("always")
    val timezone = text("timezone").nullable()
    val transitionDays = integer("transition_days").default(60)
    val archiveDays = integer("archive_days").default(7)
    val strictBlocking = bool("strict_blocking").default(false)
    val grayscaleThumbnails = bool("grayscale_thumbnails").default(false)
    val startedAt = long("started_at").default(0)
    val disableAt = long("disable_at").nullable()
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(userId)
}
