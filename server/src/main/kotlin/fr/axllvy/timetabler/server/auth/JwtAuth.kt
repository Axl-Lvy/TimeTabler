package fr.axllvy.timetabler.server.auth

import co.touchlab.kermit.Logger
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import java.net.URI
import java.util.concurrent.TimeUnit

/** Name of the authentication provider every admin route goes through. */
private const val ADMIN_AUTH = "admin-jwt"

/** Scope the Logto `admin` role grants on the API resource. */
const val ADMIN_SCOPE = "admin"

/**
 * Longest `sub` accepted. Providers stay well under this; the cap exists so a forged token cannot
 * make the server log or store an unbounded string.
 */
private const val MAX_SUBJECT_LENGTH = 255

/** How many keys the JWKS cache holds. An issuer publishes one or two, plus one mid rotation. */
private const val JWKS_CACHE_SIZE = 10L

/** How long a cached key stays usable before it is refetched. */
private const val JWKS_CACHE_TTL_MINUTES = 10L

/** Ceiling on JWKS fetches, so an unknown `kid` cannot be used to hammer the issuer. */
private const val JWKS_FETCHES_PER_MINUTE = 10L

/** The Logto tenant whose access tokens admin routes accept. */
data class AuthConfig(val issuer: String, val audience: String, val jwksUrl: URI)

/** The caller of an admin route: the token's `sub` and the scopes it was granted. */
data class Caller(val subject: String, val scopes: Set<String>)

/**
 * Installs bearer token authentication for [adminRoutes]. `iss`, `aud`, `exp` and the signature are
 * all checked, with no clock leeway; without [config], every admin request is refused.
 */
fun Application.installAdminAuth(config: AuthConfig?) {
  if (config == null) {
    Logger.withTag("AdminAuth").w { "TT_AUTH_ISSUER is not set: admin routes refuse every request" }
  }
  install(Authentication) {
    if (config == null) {
      bearer(ADMIN_AUTH) { authenticate { null } }
    } else {
      jwt(ADMIN_AUTH) {
        verifier(jwksProvider(config.jwksUrl), config.issuer) {
          withAudience(config.audience)
          acceptLeeway(0)
        }
        validate { credential ->
          credential.subject
            ?.takeIf { it.isNotBlank() && it.length <= MAX_SUBJECT_LENGTH }
            ?.let { Caller(it, credential.payload.getClaim("scope").asString().orEmpty().scopes()) }
        }
        challenge { _, _ ->
          call.respondText(
            "A valid bearer token is required",
            status = HttpStatusCode.Unauthorized,
          )
        }
      }
    }
  }
}

/** Routes [build] behind a valid token carrying [ADMIN_SCOPE]: 401 without one, 403 without it. */
fun Route.adminRoutes(build: Route.() -> Unit): Route =
  authenticate(ADMIN_AUTH) {
    install(RequireAdminScope)
    build()
  }

private val RequireAdminScope =
  createRouteScopedPlugin("RequireAdminScope") {
    on(AuthenticationChecked) { call ->
      val caller = call.principal<Caller>() ?: return@on
      if (ADMIN_SCOPE !in caller.scopes) {
        call.respondText(
          "The token does not carry the $ADMIN_SCOPE scope",
          status = HttpStatusCode.Forbidden,
        )
      }
    }
  }

/**
 * The caller of an admin route.
 *
 * @throws IllegalArgumentException when read outside [adminRoutes].
 */
val ApplicationCall.caller: Caller
  get() = requireNotNull(principal<Caller>()) { "caller is only available inside adminRoutes" }

private fun String.scopes(): Set<String> = split(' ').filter { it.isNotEmpty() }.toSet()

/** The key source: cached with a TTL, refetched on an unknown `kid`, and rate limited. */
private fun jwksProvider(url: URI): JwkProvider =
  JwkProviderBuilder(url.toURL())
    .cached(JWKS_CACHE_SIZE, JWKS_CACHE_TTL_MINUTES, TimeUnit.MINUTES)
    .rateLimited(JWKS_FETCHES_PER_MINUTE, 1, TimeUnit.MINUTES)
    .build()
