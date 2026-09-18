package dev.typetype.server.services

object OidcConfigLoader {
    fun fromEnvironment(): OidcConfig? {
        val issuer = firstNonBlank("OIDC_ISSUER", "OIDC_ISSUER_URL")?.trimEnd('/') ?: return null
        val clientId = System.getenv("OIDC_CLIENT_ID")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val clientSecret = System.getenv("OIDC_CLIENT_SECRET")?.takeIf { it.isNotBlank() } ?: return null
        val discoveryUrl = System.getenv("OIDC_DISCOVERY_URL")?.trim()?.takeIf { it.isNotEmpty() }
            ?: "$issuer/.well-known/openid-configuration"
        return OidcConfig(
            issuer = issuer,
            clientId = clientId,
            clientSecret = clientSecret,
            discoveryUrl = discoveryUrl,
            scopes = System.getenv("OIDC_SCOPES")?.trim()?.takeIf { it.isNotEmpty() } ?: "openid email profile",
            providerName = System.getenv("OIDC_PROVIDER_NAME")?.trim()?.takeIf { it.isNotEmpty() } ?: "OIDC",
        )
    }

    private fun firstNonBlank(vararg names: String): String? =
        names.firstNotNullOfOrNull { System.getenv(it)?.takeIf(String::isNotBlank) }
}
