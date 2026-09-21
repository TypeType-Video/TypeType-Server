package dev.typetype.server.services

import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.schabi.newpipe.extractor.Page

@Serializable
data class PageSurrogate(
    val url: String?,
    val id: String?,
    val ids: List<String>?,
    val cookies: Map<String, String>?,
    val body: String?,
)

fun Page.toCursor(): String {
    val surrogate = PageSurrogate(
        url = url,
        id = id,
        ids = ids,
        cookies = cookies,
        body = body?.let { Base64.getEncoder().encodeToString(it) },
    )
    return Base64.getEncoder().encodeToString(Json.encodeToString(surrogate).toByteArray())
}

fun String.toPage(): Page {
    val json = String(Base64.getDecoder().decode(this))
    val surrogate = Json.decodeFromString<PageSurrogate>(json)
    return Page(
        surrogate.url,
        surrogate.id,
        surrogate.ids,
        surrogate.cookies,
        surrogate.body?.let { Base64.getDecoder().decode(it) },
    )
}
