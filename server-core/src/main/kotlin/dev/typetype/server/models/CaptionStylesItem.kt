package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class CaptionStylesItem(
    val fontFamily: String = "",
    val fontSize: String = "",
    val textColor: String = "",
    val textOpacity: String = "",
    val textShadow: String = "",
    val textBg: String = "",
    val textBgOpacity: String = "",
    val displayBg: String = "",
    val displayBgOpacity: String = "",
)
