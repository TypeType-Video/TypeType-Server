package dev.typetype.server.services

import kotlinx.serialization.KSerializer

class PublicCacheArea<T>(
    val name: String,
    val serializer: KSerializer<T>,
    val keepLastGood: Boolean = true,
    val backoffOnFailure: Boolean = true,
    val admission: (T) -> CacheAdmission,
)
