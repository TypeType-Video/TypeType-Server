package dev.typetype.server.portability

import java.sql.Connection

class NewPipeArchiveRecordWriter(
    private val db: Connection,
    private val target: NewPipeArchiveTarget,
) {
    fun write(source: PortabilityRecordSource, categories: Set<PortabilityCategory>) {
        if (PortabilityCategory.SUBSCRIPTIONS in categories) subscriptions(source)
        if (PortabilityCategory.SUBSCRIPTION_GROUPS in categories) groups(source)
        if (PortabilityCategory.HISTORY in categories) history(source)
        if (PortabilityCategory.PLAYLISTS in categories) playlists(source)
        if (PortabilityCategory.PROGRESS in categories) progress(source)
        if (PortabilityCategory.SEARCH_HISTORY in categories) searchHistory(source)
        if (PortabilityCategory.SAVED_PLAYLISTS in categories) savedPlaylists(source)
    }

    private fun subscriptions(source: PortabilityRecordSource) {
        source.forEach(PortabilityCategory.SUBSCRIPTIONS) { record ->
            val item = record as PortabilitySubscription
            if (NewPipeProvider.supported(item.channelUrl, target)) ensureSubscription(item.channelUrl, item.name, item.avatarUrl)
        }
    }

    private fun groups(source: PortabilityRecordSource) {
        var order = 0
        source.forEach(PortabilityCategory.SUBSCRIPTION_GROUPS) { record ->
            if (record !is PortabilitySubscriptionGroup) return@forEach
            val groupId = db.insertId(
                "INSERT INTO feed_group(name,icon_id,sort_order) VALUES(?,?,?)",
                record.name,
                0,
                order++,
            )
            source.forEachChild(PortabilityCategory.SUBSCRIPTION_GROUPS, record.name) { child ->
                val membership = child as? PortabilitySubscriptionGroupMembership ?: return@forEachChild
                if (!NewPipeProvider.supported(membership.channelUrl, target)) return@forEachChild
                val subscriptionId = ensureSubscription(membership.channelUrl, "", "")
                db.execute(
                    "INSERT OR IGNORE INTO feed_group_subscription_join(group_id,subscription_id) VALUES(?,?)",
                    groupId,
                    subscriptionId,
                )
            }
        }
    }

    private fun history(source: PortabilityRecordSource) {
        source.forEach(PortabilityCategory.HISTORY) { record ->
            val item = record as PortabilityHistory
            if (!NewPipeProvider.supported(item.video.url, target)) return@forEach
            val streamId = ensureVideo(item.video)
            db.execute(
                "INSERT OR IGNORE INTO stream_history(stream_id,access_date,repeat_count) VALUES(?,?,1)",
                streamId,
                item.watchedAt,
            )
        }
    }

    private fun playlists(source: PortabilityRecordSource) {
        var order = 0
        source.forEach(PortabilityCategory.PLAYLISTS) { record ->
            val playlist = record as? PortabilityPlaylist ?: return@forEach
            val id = if (target.pipePipe) {
                db.insertId("INSERT INTO playlists(name,thumbnail_url,display_index) VALUES(?,'',?)", playlist.name, order++)
            } else {
                db.insertId(
                    "INSERT INTO playlists(name,is_thumbnail_permanent,thumbnail_stream_id,display_index) VALUES(?,0,-1,?)",
                    playlist.name,
                    order++,
                )
            }
            source.forEachChild(PortabilityCategory.PLAYLISTS, playlist.sourceId) { child ->
                val item = child as? PortabilityPlaylistVideo ?: return@forEachChild
                if (!NewPipeProvider.supported(item.video.url, target)) return@forEachChild
                db.execute(
                    "INSERT OR REPLACE INTO playlist_stream_join(playlist_id,stream_id,join_index) VALUES(?,?,?)",
                    id,
                    ensureVideo(item.video),
                    item.position,
                )
            }
        }
    }

    private fun progress(source: PortabilityRecordSource) {
        source.forEach(PortabilityCategory.PROGRESS) { record ->
            val item = record as PortabilityProgress
            if (!NewPipeProvider.supported(item.videoUrl, target)) return@forEach
            val streamId = ensureVideo(PortabilityVideo(item.videoUrl))
            db.execute("INSERT OR REPLACE INTO stream_state(stream_id,progress_time) VALUES(?,?)", streamId, item.positionSeconds)
        }
    }

    private fun searchHistory(source: PortabilityRecordSource) {
        source.forEach(PortabilityCategory.SEARCH_HISTORY) { record ->
            val item = record as PortabilitySearchHistory
            db.execute(
                "INSERT INTO search_history(creation_date,service_id,search) VALUES(?,0,?)",
                item.searchedAt,
                item.term,
            )
        }
    }

    private fun savedPlaylists(source: PortabilityRecordSource) {
        var order = 0
        source.forEach(PortabilityCategory.SAVED_PLAYLISTS) { record ->
            val item = record as PortabilitySavedPlaylist
            val serviceId = NewPipeProvider.serviceId(item.url) ?: return@forEach
            if (!target.pipePipe && serviceId != 0) return@forEach
            db.execute(
                "INSERT OR IGNORE INTO remote_playlists(service_id,name,url,thumbnail_url,uploader,display_index,stream_count) VALUES(?,?,?,?,?,?,?)",
                serviceId,
                item.title,
                item.url,
                item.thumbnailUrl,
                item.uploaderName,
                order++,
                item.streamCount,
            )
        }
    }

    private fun ensureSubscription(url: String, name: String, avatar: String): Long {
        val serviceId = requireNotNull(NewPipeProvider.serviceId(url))
        db.execute(
            "INSERT OR IGNORE INTO subscriptions(service_id,url,name,avatar_url,subscriber_count,description,notification_mode) VALUES(?,?,?,?,NULL,NULL,0)",
            serviceId,
            url,
            name,
            avatar,
        )
        return db.long("SELECT uid FROM subscriptions WHERE service_id=? AND url=?", serviceId, url)
    }

    private fun ensureVideo(video: PortabilityVideo): Long {
        val serviceId = requireNotNull(NewPipeProvider.serviceId(video.url))
        val columns = if (target.pipePipe) {
            "service_id,url,title,stream_type,duration,uploader,uploader_url,thumbnail_url,view_count,textual_upload_date,upload_date,is_upload_date_approximation,is_paid"
        } else {
            "service_id,url,title,stream_type,duration,uploader,uploader_url,thumbnail_url,view_count,textual_upload_date,upload_date,is_upload_date_approximation"
        }
        val values = if (target.pipePipe) "?,?,?,?,?,?,?,?,?,NULL,?,0,0" else "?,?,?,?,?,?,?,?,?,NULL,?,0"
        db.execute(
            "INSERT OR IGNORE INTO streams($columns) VALUES($values)",
            serviceId,
            video.url,
            video.title,
            "VIDEO_STREAM",
            video.durationSeconds,
            video.channelName,
            video.channelUrl,
            video.thumbnailUrl,
            video.viewCount,
            video.publishedAt.takeIf { it >= 0L },
        )
        return db.long("SELECT uid FROM streams WHERE service_id=? AND url=?", serviceId, video.url)
    }
}

private fun Connection.execute(sql: String, vararg values: Any?) {
    prepareStatement(sql).use { statement ->
        values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
        statement.executeUpdate()
    }
}

private fun Connection.insertId(sql: String, vararg values: Any?): Long {
    execute(sql, *values)
    return long("SELECT last_insert_rowid()")
}

private fun Connection.long(sql: String, vararg values: Any?): Long = prepareStatement(sql).use { statement ->
    values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
    statement.executeQuery().use { rows -> check(rows.next()); rows.getLong(1) }
}
