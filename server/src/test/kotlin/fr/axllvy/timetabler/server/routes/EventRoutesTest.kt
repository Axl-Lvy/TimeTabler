package fr.axllvy.timetabler.server.routes

import fr.axllvy.timetabler.domain.Revision
import fr.axllvy.timetabler.server.timeTablerModule
import fr.axllvy.timetabler.server.PostgresTestDb
import fr.axllvy.timetabler.server.events.EventStore
import fr.axllvy.timetabler.server.offlineFetcher
import fr.axllvy.timetabler.server.posters.PosterCache
import fr.axllvy.timetabler.server.events.ScrapedListing
import fr.axllvy.timetabler.wire.EventsPage
import fr.axllvy.timetabler.wire.ListingStatus
import fr.axllvy.timetabler.wire.Source
import fr.axllvy.timetabler.wire.WIRE_JSON
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.time.Instant

class EventRoutesTest {

  private val store = EventStore(PostgresTestDb.freshDataSource())

  private fun stored(id: String) =
    store.upsert(
      ScrapedListing(
        Source.DICE,
        id,
        "https://dice.fm/event/$id",
        "Night $id",
        Instant.parse("2026-10-30T22:30:00Z"),
        null,
        "Rex Club",
        null,
        ListingStatus.ON_SALE,
        null,
        emptyList(),
      )
    )

  private fun app(test: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) =
    testApplication {
      application { timeTablerModule(store, PosterCache(createTempDirectory(), offlineFetcher()), staticDir = null) }
      test()
    }

  @Test
  fun `the feed returns stored events`() = app {
    stored("a")
    stored("b")

    val response = client.get("/v1/events?since=0")

    response.status shouldBe HttpStatusCode.OK
    val page = WIRE_JSON.decodeFromString(EventsPage.serializer(), response.bodyAsText())
    page.events.map { it.title } shouldBe listOf("Night a", "Night b")
    page.hasMore shouldBe false
  }

  @Test
  fun `the feed starts from the given revision`() = app {
    stored("a")
    val after = store.page(Revision.ZERO, 10).next
    stored("b")

    val page =
      WIRE_JSON.decodeFromString(
        EventsPage.serializer(),
        client.get("/v1/events?since=${after.value}").bodyAsText(),
      )

    page.events.map { it.title } shouldBe listOf("Night b")
  }

  @Test
  fun `a malformed since is a bad request`() = app {
    client.get("/v1/events?since=abc").status shouldBe HttpStatusCode.BadRequest
  }

  @Test
  fun `a malformed limit is a bad request`() = app {
    client.get("/v1/events?limit=0").status shouldBe HttpStatusCode.BadRequest
  }

  @Test
  fun `health answers ok`() = app { client.get("/health").status shouldBe HttpStatusCode.OK }

  @Test
  fun `page limit defaults clamps and rejects`() {
    parsePageLimit(null, default = 500, max = 2000) shouldBe 500
    parsePageLimit("5000", default = 500, max = 2000) shouldBe 2000
    parsePageLimit("20", default = 500, max = 2000) shouldBe 20
    parsePageLimit("0", default = 500, max = 2000).shouldBeNull()
    parsePageLimit("abc", default = 500, max = 2000).shouldBeNull()
  }
}
