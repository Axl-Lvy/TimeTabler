package fr.axllvy.timetabler.server.dice

import fr.axllvy.timetabler.domain.Revision
import fr.axllvy.timetabler.server.PostgresTestDb
import fr.axllvy.timetabler.server.events.EventStore
import fr.axllvy.timetabler.server.fixture
import fr.axllvy.timetabler.server.http.PoliteFetcher
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class DiceScraperTest {

  private val now = Instant.parse("2026-11-07T09:00:00Z")
  private val clock =
    object : Clock {
      override fun now() = now
    }
  private val store = EventStore(PostgresTestDb.freshDataSource(), clock)
  private val baron = "6ae30f3eff6a4e000149d810"
  private val emptyDay =
    """<script id="__NEXT_DATA__">{"props":{"pageProps":{"events":[],"nextCursor":null}}}</script>"""
  private val requested = mutableListOf<String>()
  private val djDay = DiceBrowseParser.dayUrl("dj", LocalDate(2026, 11, 7))
  private val house =
    "https://dice.fm/browse/paris-5b23e8a0e63cc224a4c36a2d/music/dj/house?from=2026-11-07&until=2026-11-07"

  private fun browsePage(
    ids: List<String>,
    overflow: Boolean = false,
    subGenres: List<String> = emptyList(),
  ): String {
    val events =
      ids.joinToString(",") {
        """{"id":"$it","name":"Night $it","dates":{"event_start_date":"2026-11-07T23:00:00+01:00"},"venues":[{"name":"Rex Club"}]}"""
      }
    val filters =
      subGenres.joinToString(",") { """{"activateLink":"${it.removePrefix("https://dice.fm")}"}""" }
    val cursor = if (overflow) "\"next\"" else "null"
    return """<script id="__NEXT_DATA__">{"props":{"pageProps":{"events":[$events],"nextCursor":$cursor,"secondaryFilters":[$filters]}}}</script>"""
  }

  private fun TestScope.scraper(
    route: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData? = { null }
  ): DiceScraper {
    val engine = MockEngine { request ->
      val url = request.url.toString()
      requested += url
      route(request)
        ?: when {
          url == DiceBrowseParser.dayUrl("dj", LocalDate(2026, 11, 7)) ->
            respond(fixture("dice/day-page.html"))
          "/browse/" in url -> respond(emptyDay)
          url.endsWith(baron) -> respond(fixture("dice/event-with-lineup.html"))
          else -> respond(fixture("dice/event-without-lineup.html"))
        }
    }
    val fetcher =
      PoliteFetcher(HttpClient(engine), "TimeTabler/test", timeSource = testScheduler.timeSource)
    return DiceScraper(fetcher, store, clock)
  }

  private fun storedTitles() = store.page(Revision.ZERO, 1000).events.map { it.title }

  @Test
  fun `stores every listing of the window with its lineup`() = runTest {
    val report = scraper().scrapeAgenda()

    report.failed.shouldBeNull()
    report.upserted shouldBe 5
    report.pages shouldBe 61 * DICE_CATEGORIES.size
    storedTitles().size shouldBe 5
    store.page(Revision.ZERO, 1000).events.single { it.title.startsWith("Baron") }.lineup shouldBe
      listOf("Baron Von Trax", "Cattoni", "Gray", "MaaxD", "VolviK")
  }

  @Test
  fun `an overflowing day stores the events of its sub-genre pages`() = runTest {
    val report = scraper {
      when (it.url.toString()) {
        djDay -> respond(browsePage(listOf("a1"), overflow = true, subGenres = listOf(house)))
        house -> respond(browsePage(listOf("a1", "b1")))
        else -> null
      }
    }
      .scrapeAgenda()

    report.failed.shouldBeNull()
    report.overflowedDays shouldBe 1
    report.overflowedSubGenres shouldBe 0
    storedTitles() shouldContainExactlyInAnyOrder listOf("Night a1", "Night b1")
  }

  @Test
  fun `a day that does not overflow fetches no sub-genre page`() = runTest {
    val report = scraper {
      if (it.url.toString() == djDay) respond(browsePage(listOf("a1"), subGenres = listOf(house)))
      else null
    }
      .scrapeAgenda()

    report.overflowedDays shouldBe 0
    requested.none { it == house } shouldBe true
  }

  @Test
  fun `an overflowing sub-genre page is counted and the run continues`() = runTest {
    val report = scraper {
      when (it.url.toString()) {
        djDay -> respond(browsePage(listOf("a1"), overflow = true, subGenres = listOf(house)))
        house -> respond(browsePage(listOf("b1"), overflow = true))
        else -> null
      }
    }
      .scrapeAgenda()

    report.failed.shouldBeNull()
    report.overflowedSubGenres shouldBe 1
    report.pages shouldBe 61 * DICE_CATEGORIES.size + 1
  }

  @Test
  fun `a throttled sub-genre page stops the run without lineups`() = runTest {
    val report = scraper {
      when (it.url.toString()) {
        djDay -> respond(browsePage(listOf("a1"), overflow = true, subGenres = listOf(house)))
        house -> respondError(HttpStatusCode.TooManyRequests)
        else -> null
      }
    }
      .scrapeAgenda()

    report.failed!! shouldContain "429"
    requested.filter { "/event/" in it }.shouldBeEmpty()
  }

  @Test
  fun `a throttled page stops the run and keeps what was stored`() = runTest {
    val party = DiceBrowseParser.dayUrl("party", LocalDate(2026, 11, 7))

    val report = scraper {
      if (it.url.toString() == party) respondError(HttpStatusCode.TooManyRequests) else null
    }
      .scrapeAgenda()

    report.failed!! shouldContain "429"
    storedTitles().size shouldBe 5
    requested.filter { "/event/" in it }.shouldBeEmpty()
    store.page(Revision.ZERO, 1000).removed.shouldBeEmpty()
    requested.none { it == DiceBrowseParser.dayUrl("gig", LocalDate(2026, 11, 7)) } shouldBe true
  }

  @Test
  fun `a page without page data stops the run`() = runTest {
    val party = DiceBrowseParser.dayUrl("party", LocalDate(2026, 11, 7))

    val report = scraper {
      if (it.url.toString() == party) respond("<html>Checking your browser</html>") else null
    }
      .scrapeAgenda()

    report.failed!! shouldContain "no page data"
  }

  @Test
  fun `a throttled lineup stops further lineup requests`() = runTest {
    val report = scraper {
      if ("/event/" in it.url.toString()) respondError(HttpStatusCode.TooManyRequests) else null
    }
      .scrapeAgenda()

    report.failed.shouldBeNull()
    requested.count { "/event/" in it } shouldBe 1
  }

  @Test
  fun `a timed out page is retried once`() = runTest {
    val party = DiceBrowseParser.dayUrl("party", LocalDate(2026, 11, 7))
    var attempts = 0

    val report = scraper {
      if (it.url.toString() == party && attempts++ == 0)
        throw java.io.IOException("Request timeout has expired")
      else null
    }
      .scrapeAgenda()

    report.failed.shouldBeNull()
    attempts shouldBe 2
  }

  @Test
  fun `a page timing out twice stops the run`() = runTest {
    val party = DiceBrowseParser.dayUrl("party", LocalDate(2026, 11, 7))

    val report = scraper {
      if (it.url.toString() == party) throw java.io.IOException("Request timeout has expired")
      else null
    }
      .scrapeAgenda()

    report.failed!! shouldContain "timeout"
    storedTitles().size shouldBe 5
  }

  @Test
  fun `an event page without data leaves the lineup empty and the run continues`() = runTest {
    val report = scraper {
      if (it.url.toString().endsWith(baron)) respond(fixture("dice/event-without-data.html"))
      else null
    }
      .scrapeAgenda()

    report.failed.shouldBeNull()
    store
      .page(Revision.ZERO, 1000)
      .events
      .single { it.title.startsWith("Baron") }
      .lineup
      .shouldBeEmpty()
  }

  @Test
  fun `unchanged listings are not fetched again`() = runTest {
    scraper().scrapeAgenda()
    requested.clear()

    scraper().scrapeAgenda()

    requested.filter { "/event/" in it }.shouldBeEmpty()
  }

  @Test
  fun `a lineup that failed to load is fetched on the next run`() = runTest {
    scraper {
      if (it.url.toString().endsWith(baron)) respondError(HttpStatusCode.GatewayTimeout) else null
    }
      .scrapeAgenda()
    requested.clear()

    scraper().scrapeAgenda()

    requested.filter { "/event/" in it } shouldContainExactlyInAnyOrder
      listOf("https://dice.fm/event/$baron")
    store
      .page(Revision.ZERO, 1000)
      .events
      .single { it.title.startsWith("Baron") }
      .lineup
      .size shouldBe 5
  }

  private suspend fun TestScope.scrapeDjDay(vararg pages: String): ScrapeReport {
    var report: ScrapeReport? = null
    for (html in pages) {
      report = scraper { if (it.url.toString() == djDay) respond(html) else null }.scrapeAgenda()
    }
    return report!!
  }

  @Test
  fun `a listing missing from a complete scrape disappears from the feed`() = runTest {
    scrapeDjDay(browsePage(listOf("a1", "b1")))
    val highest = store.page(Revision.ZERO, 1000).next

    val report = scrapeDjDay(browsePage(listOf("a1")))

    report.gone shouldBe 1
    store.page(highest, 1000).removed.size shouldBe 1
    storedTitles() shouldContainExactlyInAnyOrder listOf("Night a1")
  }

  @Test
  fun `a failed scrape removes nothing`() = runTest {
    scrapeDjDay(browsePage(listOf("a1", "b1")))
    val laterParty = DiceBrowseParser.dayUrl("party", LocalDate(2026, 11, 8))

    val report = scraper {
      when (it.url.toString()) {
        djDay -> respond(browsePage(listOf("a1")))
        laterParty -> respondError(HttpStatusCode.TooManyRequests)
        else -> null
      }
    }
      .scrapeAgenda()

    report.failed!! shouldContain "429"
    report.gone shouldBe 0
    storedTitles() shouldContainExactlyInAnyOrder listOf("Night a1", "Night b1")
    store.page(Revision.ZERO, 1000).removed.shouldBeEmpty()
  }

  @Test
  fun `a day that overflows without sub-genres removes nothing`() = runTest {
    scrapeDjDay(browsePage(listOf("a1", "b1")))

    val report = scrapeDjDay(browsePage(listOf("a1"), overflow = true))

    report.gone shouldBe 0
    storedTitles() shouldContainExactlyInAnyOrder listOf("Night a1", "Night b1")
  }

  @Test
  fun `a day with an unreadable event removes nothing`() = runTest {
    scrapeDjDay(browsePage(listOf("a1", "b1")))
    val withBroken =
      browsePage(listOf("a1")).replace("\"events\":[", "\"events\":[{\"id\":\"b1\"},")

    val report = scrapeDjDay(withBroken)

    report.gone shouldBe 0
    storedTitles() shouldContainExactlyInAnyOrder listOf("Night a1", "Night b1")
  }

  @Test
  fun `a scrape that sees no event at all removes nothing`() = runTest {
    scrapeDjDay(browsePage(listOf("a1", "b1")))

    val report = scrapeDjDay(emptyDay)

    report.failed.shouldBeNull()
    report.gone shouldBe 0
    storedTitles() shouldContainExactlyInAnyOrder listOf("Night a1", "Night b1")
  }

  @Test
  fun `refresh fetches only events starting in the next two days`() = runTest {
    scraper().scrapeAgenda()
    requested.clear()

    scraper().refreshSoon()

    requested.map { it.substringAfterLast('/') } shouldContainExactlyInAnyOrder
      listOf(
        "6ad5f2180594730001f32ed4",
        "6ae30f3eff6a4e000149d810",
        "6ad5e92e9609160001678e6c",
        "6ac7bec70dbdde000190897f",
        "6ab11603d53a780001f34604",
      )
  }
}
