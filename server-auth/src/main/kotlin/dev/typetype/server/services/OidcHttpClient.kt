package dev.typetype.server.services

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class OidcHttpClient(private val client: OkHttpClient) {
    fun metadata(config: OidcConfig): OidcProviderMetadata {
        val json = getJson(config.discoveryUrl)
        return OidcProviderMetadata(
            issuer = json.getString("issuer"),
            authorizationEndpoint = json.getString("authorization_endpoint"),
            tokenEndpoint = json.getString("token_endpoint"),
            jwksUri = json.getString("jwks_uri"),
        )
    }

    fun jwks(jwksUri: String): JSONObject = getJson(jwksUri)

    fun exchangeCode(config: OidcConfig, metadata: OidcProviderMetadata, code: String, redirectUri: String): JSONObject {
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("client_id", config.clientId)
            .add("client_secret", config.clientSecret)
            .build()
        val request = Request.Builder().url(metadata.tokenEndpoint).post(body).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body.string()
            if (!response.isSuccessful) throw IllegalStateException("OIDC token exchange failed")
            return JSONObject(raw)
        }
    }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            val raw = response.body.string()
            if (!response.isSuccessful) throw IllegalStateException("OIDC provider request failed")
            return JSONObject(raw)
        }
    }
}
