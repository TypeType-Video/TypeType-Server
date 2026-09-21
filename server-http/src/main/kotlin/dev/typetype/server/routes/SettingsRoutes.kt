package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.SettingsItem
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SettingsService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

fun Route.settingsRoutes(settingsService: SettingsService, authService: AuthService) {
    get("/settings") {
        call.withJwtAuth(authService) { userId -> call.respond(settingsService.get(userId)) }
    }
    put("/settings") {
        call.withJwtAuth(authService) { userId ->
            val patch = runCatching { call.receive<JsonObject>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            val current = settingsService.get(userId)
            val merged = JsonObject(SETTINGS_JSON.encodeToJsonElement(current).jsonObject + patch)
            val settings = runCatching { SETTINGS_JSON.decodeFromJsonElement(SettingsItem.serializer(), merged) }
                .getOrElse { return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body")) }
            call.respond(settingsService.upsert(userId, settings))
        }
    }
}

private val SETTINGS_JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }
