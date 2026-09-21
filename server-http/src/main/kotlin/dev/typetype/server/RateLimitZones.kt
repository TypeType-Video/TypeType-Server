package dev.typetype.server

import io.ktor.server.plugins.ratelimit.RateLimitName

val EXTRACTION_ZONE = RateLimitName("extraction")
val DEARROW_ZONE = RateLimitName("dearrow")
val STREAMS_ZONE = RateLimitName("streams")
val CHANNEL_ZONE = RateLimitName("channel")
val PROXY_ZONE = RateLimitName("proxy")
val PROXY_STORYBOARD_ZONE = RateLimitName("proxy-storyboard")
val USER_DATA_ZONE = RateLimitName("user-data")
