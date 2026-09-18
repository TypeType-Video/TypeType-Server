package dev.typetype.server.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SponsorBlockMode(val storageValue: String) {
    @SerialName("auto_skip")
    AUTO_SKIP("auto_skip"),

    @SerialName("mark_only")
    MARK_ONLY("mark_only"),

    @SerialName("disabled")
    DISABLED("disabled"),
}

fun String.toSponsorBlockMode(): SponsorBlockMode =
    SponsorBlockMode.entries.firstOrNull { it.storageValue == this } ?: SponsorBlockMode.AUTO_SKIP
