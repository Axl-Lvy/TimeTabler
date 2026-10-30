package fr.axllvy.timetabler.server

import fr.axllvy.timetabler.server.auth.AuthConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.net.URI
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class ServerConfigTest {

  private val required =
    mapOf("TT_DB_URL" to "jdbc:postgresql://db/tt", "TT_DB_USER" to "tt", "TT_DB_PASSWORD" to "pw")

  @Test
  fun `removals are kept thirty days by default`() {
    val config = ServerConfig.fromEnv(required)

    config.removalRetention shouldBe 30.days
  }

  @Test
  fun `the removal retention is read from the environment`() {
    val config = ServerConfig.fromEnv(required + ("TT_REMOVAL_RETENTION" to "36h"))

    config.removalRetention shouldBe 36.hours
  }

  @Test
  fun `a removal retention that is not positive is refused`() {
    val env = required + ("TT_REMOVAL_RETENTION" to "-1d")

    shouldThrow<IllegalArgumentException> { ServerConfig.fromEnv(env) }
  }

  @Test
  fun `admin sign-in is off when no issuer is set`() {
    val config = ServerConfig.fromEnv(required)

    config.auth.shouldBeNull()
  }

  @Test
  fun `the jwks url defaults to the issuer's`() {
    val env =
      required +
        mapOf(
          "TT_AUTH_ISSUER" to "https://tenant.logto.app/oidc",
          "TT_AUTH_AUDIENCE" to "https://timetabler.example/api",
        )

    val config = ServerConfig.fromEnv(env)

    config.auth shouldBe
      AuthConfig(
        "https://tenant.logto.app/oidc",
        "https://timetabler.example/api",
        URI("https://tenant.logto.app/oidc/jwks"),
      )
  }

  @Test
  fun `the jwks url is read from the environment`() {
    val env =
      required +
        mapOf(
          "TT_AUTH_ISSUER" to "https://tenant.logto.app/oidc",
          "TT_AUTH_AUDIENCE" to "https://timetabler.example/api",
          "TT_AUTH_JWKS_URL" to "http://localhost:3001/oidc/jwks",
        )

    val config = ServerConfig.fromEnv(env)

    config.auth?.jwksUrl shouldBe URI("http://localhost:3001/oidc/jwks")
  }

  @Test
  fun `an issuer without an audience is refused`() {
    val env = required + ("TT_AUTH_ISSUER" to "https://tenant.logto.app/oidc")

    shouldThrow<IllegalStateException> { ServerConfig.fromEnv(env) }
  }

  @Test
  fun `Shotgun scraping is off by default`() {
    val config = ServerConfig.fromEnv(required)

    config.shotgunEnabled shouldBe false
  }

  @Test
  fun `Shotgun scraping is turned on from the environment`() {
    val config = ServerConfig.fromEnv(required + ("TT_SHOTGUN_ENABLED" to "true"))

    config.shotgunEnabled shouldBe true
  }

  @Test
  fun `a Shotgun switch that is not a boolean is refused`() {
    val env = required + ("TT_SHOTGUN_ENABLED" to "yes")

    shouldThrow<IllegalArgumentException> { ServerConfig.fromEnv(env) }
  }
}
