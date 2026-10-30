package fr.axllvy.timetabler.server.routes

import fr.axllvy.timetabler.server.PostgresTestDb
import fr.axllvy.timetabler.server.dice.ScrapeReport
import fr.axllvy.timetabler.server.events.EventStore
import fr.axllvy.timetabler.server.health.ScrapeRuns
import fr.axllvy.timetabler.server.offlineFetcher
import fr.axllvy.timetabler.server.posters.PosterCache
import fr.axllvy.timetabler.server.timeTablerModule
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject

class HealthRouteTest {

  private val db = PostgresTestDb.freshDataSource()
  private val store = EventStore(db)
  private val runs = ScrapeRuns(store)
  private val at = Instant.parse("2026-11-07T03:00:00Z")

  @AfterTest
  fun close() {
    db.close()
  }

  private fun app(test: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
    application {
      timeTablerModule(
        store,
        PosterCache(createTempDirectory(), offlineFetcher()),
        runs,
        staticDir = null,
      )
    }
    test()
  }

  private suspend fun ApplicationTestBuilder.health() = client.get("/health")

  @Test
  fun `health writes every field of the last run of each scrape job`() = app {
    runs.record("agenda", ScrapeReport(183, 0, "429 Too Many Requests"), at)

    val response = health()

    response.status shouldBe HttpStatusCode.OK
    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
    body.getValue("status") shouldBe Json.parseToJsonElement("\"ok\"")
    body.getValue("scrapes").jsonObject.getValue("agenda") shouldBe
      Json.parseToJsonElement(
        """{"lastAttemptAt":"2026-11-07T03:00:00Z","lastSuccessAt":null,"consecutiveFailures":1,
          "failure":"429 Too Many Requests","pages":183,"listings":0,"upserted":0,"lineups":0,
          "lastDay":null,"overflowedDays":0,"overflowedSubGenres":0,"posters":0}"""
      )
  }

  @Test
  fun `health answers from memory when the database is down`() = app {
    runs.record("agenda", ScrapeReport(12, 3, null), at)
    db.close()

    val response = health()

    response.status shouldBe HttpStatusCode.OK
    val agenda =
      Json.parseToJsonElement(response.bodyAsText())
        .jsonObject
        .getValue("scrapes")
        .jsonObject
        .getValue("agenda")
        .jsonObject
    agenda.getValue("pages") shouldBe Json.parseToJsonElement("12")
  }

  @Test
  fun `health has no scrapes before the saved runs are read`() = app {
    val response = health()

    response.status shouldBe HttpStatusCode.OK
    Json.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("scrapes") shouldBe JsonNull
  }
}
