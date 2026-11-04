package dev.typetype.server.services

const val YOUTUBE_SERVICE_ID = 0
const val BILIBILI_SERVICE_ID = 5
const val NICONICO_SERVICE_ID = 6

val VALID_SERVICE_IDS: Set<Int> = setOf(
    YOUTUBE_SERVICE_ID,
    BILIBILI_SERVICE_ID,
    NICONICO_SERVICE_ID,
)
