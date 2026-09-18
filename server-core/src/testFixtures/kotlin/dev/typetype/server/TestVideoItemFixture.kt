package dev.typetype.server

import dev.typetype.server.models.VideoItem

fun testVideoItem(): VideoItem = VideoItem(
    id = "https://youtube.com/watch?v=test",
    title = "Test Video",
    url = "https://youtube.com/watch?v=test",
    thumbnailUrl = "",
    uploaderName = "Test Channel",
    uploaderUrl = "https://youtube.com/@test",
    uploaderAvatarUrl = "",
    duration = 60,
    viewCount = 0,
    uploadDate = "",
    streamType = "video",
    isShortFormContent = false,
    uploaderVerified = false,
    shortDescription = null,
)
