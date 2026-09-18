package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object YoutubeTakeoutImportJobsTable : Table("youtube_takeout_import_jobs") {
    val id = text("id")
    val userId = text("user_id")
    val status = text("status")
    val phase = text("phase")
    val progress = integer("progress")
    val parseCompleted = bool("parse_completed")
    val importStarted = bool("import_started")
    val importCompleted = bool("import_completed")
    val archivePath = text("archive_path").nullable()
    val previewJson = text("preview_json").nullable()
    val error = text("error").nullable()
    val reportJson = text("report_json").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    val expiresAt = long("expires_at")
    override val primaryKey = PrimaryKey(id)
}
