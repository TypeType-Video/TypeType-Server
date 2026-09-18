package dev.typetype.server.services

enum class HomeRecommendationDeviceClass {
    MOBILE,
    DESKTOP,
    TABLET,
    TV,
    UNKNOWN;

    companion object {
        fun parse(userAgent: String?): HomeRecommendationDeviceClass {
            val value = userAgent?.lowercase().orEmpty()
            if (value.isBlank()) return UNKNOWN
            return when {
                value.contains("tv") -> TV
                value.contains("tablet") || value.contains("ipad") -> TABLET
                value.contains("mobile") || value.contains("android") || value.contains("iphone") -> MOBILE
                value.contains("windows") || value.contains("macintosh") || value.contains("linux") -> DESKTOP
                else -> UNKNOWN
            }
        }
    }
}
