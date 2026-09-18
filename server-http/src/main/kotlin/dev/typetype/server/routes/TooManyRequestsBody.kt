package dev.typetype.server.routes

import io.ktor.server.application.ApplicationCall
import io.ktor.util.AttributeKey

val TooManyRequestsBodyAttribute = AttributeKey<Unit>("preserveTooManyRequestsBody")

fun ApplicationCall.preserveTooManyRequestsBody() {
    attributes.put(TooManyRequestsBodyAttribute, Unit)
}
