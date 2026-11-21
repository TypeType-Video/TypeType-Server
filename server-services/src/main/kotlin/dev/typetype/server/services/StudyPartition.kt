package dev.typetype.server.services

internal data class StudyPartition(
    val active: Set<String> = emptySet(),
    val pending: Map<String, Long> = emptyMap(),
)
