package fr.axllvy.timetabler.server.routes

import fr.axllvy.timetabler.server.PostgresTestDb
import fr.axllvy.timetabler.server.dice.ScrapeReport
import fr.axllvy.timetabler.server.events.EventStore
import fr.axllvy.timetabler.server.health.Health
import fr.axllvy.timetabler.server.health.after
import fr.axllvy.timetabler.server.offlineFetcher
import fr.axllvy.timetabler.server.posters.PosterCache
import fr.axllvy.timetabler.server.timeTablerModule
import fr.axllvy.timetabler.wire.WIRE_JSON
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Instant

class HealthRouteTest {

  private val db = PostgresTestDb.freshDataSource()
  private val store = EventStore(db)

  @AfterTest
  fun close() {
    db.close()
  }

  private fun app(test: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
    application {
      timeTablerModule(
        store,
        PosterCache(createTempDirectory(), offlineFetcher()),
        staticDir = null,
      )
    }
    test()
  }

  private suspend fun ApplicationTestBuilder.health(): Pair<HttpStatusCode, Health> {
    val response = client.get("/health")
    return response.status to WIRE_JSON.decodeFromString(Health.serializer(), response.bodyAsText())
  }

  @Test
  fun `health reports the last run of each scrape job`() = app {
    val at = Instant.parse("2026-11-07T03:00:00Z")
    store.updateScrapeRun("agenda") { it.after(ScrapeReport(183, 0, "429 Too Many Requests"), at) }

    val (status, health) = health()

    status shouldBe HttpStatusCode.OK
    health.status shouldBe "ok"
    val agenda = health.scrapes!!.getValue("agenda")
    agenda.lastAttemptAt shouldBe at
    agenda.lastSuccessAt.shouldBeNull()
    agenda.failure shouldBe "429 Too Many Requests"
    agenda.pages shouldBe 183
  }

  @Test
  fun `health stays up when the database is down`() = app {
    db.close()

    val (status, health) = health()

    status shouldBe HttpStatusCode.OK
    health.scrapes.shouldBeNull()
  }
}
