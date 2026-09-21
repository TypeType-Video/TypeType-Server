package dev.typetype.server.routes

import dev.typetype.server.services.AudioOnlyMediaToken

internal sealed interface SabrManifestAccess {
    data object RequiresAuth : SabrManifestAccess
    data class AudioOnlyToken(val token: AudioOnlyMediaToken) : SabrManifestAccess
}
