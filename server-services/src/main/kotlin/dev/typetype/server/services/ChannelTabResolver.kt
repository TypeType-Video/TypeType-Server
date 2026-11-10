package dev.typetype.server.services

import org.schabi.newpipe.extractor.linkhandler.ChannelTabs

fun String.toChannelTab(sort: String?): String? {
    if (contains("/shorts", ignoreCase = true)) return ChannelTabs.SHORTS
    if (contains("/streams", ignoreCase = true)) return ChannelTabs.LIVESTREAMS
    if (contains("/livestreams", ignoreCase = true)) return ChannelTabs.LIVESTREAMS
    if (contains("/search", ignoreCase = true)) return ChannelTabs.SEARCH
    return if (sort != null) ChannelTabs.VIDEOS else null
}

fun String.toBaseChannelUrl(tab: String): String = substringBefore("/$tab")
    .substringBefore("/streams")
    .substringBefore('?')
    .substringBefore('#')
    .trimEnd('/')

/** Courses are listed as playlists under their own tab; any other URL lists the Playlists tab. */
fun String.playlistListTab(): String =
    if (substringBefore('?').trimEnd('/').endsWith("/${ChannelTabs.COURSES}", ignoreCase = true)) {
        ChannelTabs.COURSES
    } else {
        ChannelTabs.PLAYLISTS
    }
