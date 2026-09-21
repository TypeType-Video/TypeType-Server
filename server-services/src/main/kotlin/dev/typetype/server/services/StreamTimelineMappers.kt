package dev.typetype.server.services

import dev.typetype.server.models.PreviewFrameItem
import dev.typetype.server.models.StreamSegmentItem
import org.schabi.newpipe.extractor.stream.Frameset
import org.schabi.newpipe.extractor.stream.StreamSegment

fun Frameset.toPreviewFrameItem(): PreviewFrameItem = PreviewFrameItem(
    urls = getUrls() ?: emptyList(),
    frameWidth = getFrameWidth(),
    frameHeight = getFrameHeight(),
    totalCount = getTotalCount(),
    durationPerFrame = getDurationPerFrame(),
    framesPerPageX = getFramesPerPageX(),
    framesPerPageY = getFramesPerPageY(),
)

fun StreamSegment.toStreamSegmentItem(): StreamSegmentItem = StreamSegmentItem(
    title = getTitle() ?: "",
    startTimeSeconds = getStartTimeSeconds(),
    channelName = getChannelName(),
    url = getUrl(),
    previewUrl = getPreviewUrl(),
)
