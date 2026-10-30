package fr.axllvy.timetabler.server

import fr.axllvy.timetabler.server.auth.AuthConfig
import java.io.File
import java.net.URI
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Server settings from `TT_*` env vars. [removalRetention] is how long the feed keeps a removal;
 * [auth] is null without admin sign-in; [shotgunEnabled] turns on the Shotgun scrape jobs.
 */
data class ServerConfig(
  val dbUrl: String,
  val dbUser: String,
  val dbPassword: String,
  val port: Int,
  val staticDir: File?,
  val posterDir: Path,
  val removalRetention: Duration,
  val auth: AuthConfig?,
  val shotgunEnabled: Boolean = false,
) {
  companion object {
    /**
     * Reads the configuration from [env]; throws when a required variable is missing or invalid.
     */
    fun fromEnv(env: Map<String, String> = System.getenv()): ServerConfig {
      fun required(name: String) = checkNotNull(env[name]) { "$name is not set" }
      return ServerConfig(
        dbUrl = required("TT_DB_URL"),
        dbUser = required("TT_DB_USER"),
        dbPassword = required("TT_DB_PASSWORD"),
        port = env["TT_PORT"]?.toInt() ?: 8080,
        staticDir = env["TT_STATIC_DIR"]?.let(::File),
        posterDir = Path.of(env["TT_POSTER_DIR"] ?: "/data/posters"),
        removalRetention =
          (env["TT_REMOVAL_RETENTION"]?.let(Duration::parse) ?: 30.days).also {
            require(it.isPositive()) { "TT_REMOVAL_RETENTION must be positive, was $it" }
          },
        auth = authFromEnv(env),
        shotgunEnabled = env["TT_SHOTGUN_ENABLED"]?.toBooleanStrict() ?: false,
      )
    }

    private fun authFromEnv(env: Map<String, String>): AuthConfig? {
      val issuer = env["TT_AUTH_ISSUER"]
      val audience = env["TT_AUTH_AUDIENCE"]
      if (issuer == null && audience == null) return null
      checkNotNull(issuer) { "TT_AUTH_ISSUER is not set but TT_AUTH_AUDIENCE is" }
      checkNotNull(audience) { "TT_AUTH_AUDIENCE is not set but TT_AUTH_ISSUER is" }
      val jwksUrl = env["TT_AUTH_JWKS_URL"] ?: "${issuer.trimEnd('/')}/jwks"
      return AuthConfig(issuer, audience, URI(jwksUrl))
    }
  }
}
