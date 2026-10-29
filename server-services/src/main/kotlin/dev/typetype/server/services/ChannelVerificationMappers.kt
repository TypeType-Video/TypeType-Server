package dev.typetype.server.services

import org.schabi.newpipe.extractor.channel.ChannelVerification

fun ChannelVerification?.toApiValue(): String? = this?.name?.lowercase()
