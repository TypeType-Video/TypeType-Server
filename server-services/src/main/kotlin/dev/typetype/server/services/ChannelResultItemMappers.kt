package dev.typetype.server.services

import dev.typetype.server.models.ChannelResultItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem

fun ChannelInfoItem.toChannelResultItem(): ChannelResultItem = ChannelResultItem(
    id = url,
    name = name,
    url = url,
    thumbnailUrl = thumbnailUrl.orEmpty(),
    description = description.orEmpty(),
    subscriberCount = subscriberCount,
    streamCount = streamCount,
    isVerified = isVerified,
)
