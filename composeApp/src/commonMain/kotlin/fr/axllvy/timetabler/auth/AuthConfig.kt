package fr.axllvy.timetabler.auth

/** Logto tenant the admin signs in with and the API resource its access tokens are for. */
data class OidcSettings(val issuer: String, val audience: String, val webClientId: String) {

  /** Whether every value is filled in rather than left as a `<…>` placeholder. */
  val isConfigured: Boolean
    get() = listOf(issuer, audience, webClientId).none { '<' in it }
}

/**
 * Admin sign-in of this build. Replace the placeholders with the tenant's issuer and the SPA app id
 * once the Logto tenant exists; `audience` must equal the server's `TT_AUTH_AUDIENCE`.
 */
val ADMIN_OIDC =
  OidcSettings(
    issuer = "https://<tenant-id>.logto.app/oidc",
    audience = "https://timetabler.axl-lvy.fr/api",
    webClientId = "<spa-app-id>",
  )

/** Path of the web app the issuer redirects back to after sign-in. */
const val ADMIN_CALLBACK_PATH = "/admin/callback"
