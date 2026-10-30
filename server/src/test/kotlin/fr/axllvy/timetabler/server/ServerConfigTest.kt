package fr.axllvy.timetabler.server

import io.kotest.matchers.shouldBe
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
}
