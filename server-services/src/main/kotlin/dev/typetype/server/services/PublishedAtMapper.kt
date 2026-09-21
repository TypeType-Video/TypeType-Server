package dev.typetype.server.services

object PublishedAtMapper {
    fun fromUploaded(uploaded: Long): Long? = uploaded.takeIf { it > 0L }
}
