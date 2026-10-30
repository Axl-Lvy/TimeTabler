package fr.axllvy.timetabler.server.shotgun

import fr.axllvy.timetabler.domain.Revision
import fr.axllvy.timetabler.server.PostgresTestDb
import fr.axllvy.timetabler.server.events.EventStore
import fr.axllvy.timetabler.server.events.ScrapedListing
import fr.axllvy.timetabler.server.fixture
import fr.axllvy.timetabler.server.http.FetchResult
import fr.axllvy.timetabler.server.http.PageBrowser
import fr.axllvy.timetabler.server.http.PageLoader
import fr.axllvy.timetabler.wire.ListingStatus
import fr.axllvy.timetabler.wire.Source
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class ShotgunScraperTest {

  private val clock =
    object : Clock {
      override fun now() = Instant.parse("2026-11-07T09:00:00Z")
    }
  private val db = PostgresTestDb.freshDataSource()
  private val store = EventStore(db, clock)
  private val starts =
    mapOf(
      "a" to "2026-11-07T22:00:00.000Z",
      "b" to "2026-11-08T22:00:00.000Z",
      "c" to "2026-11-10T22:00:00.000Z",
    )

  @AfterTest
  fun close() {
    db.close()
  }

  private class FakeBrowser(private val route: (String) -> FetchResult) : PageBrowser {
    var sessions = 0
    val loaded = mutableListOf<String>()

    override suspend fun <T> session(block: suspend (PageLoader) -> T): T {
      sessions++
      return block { url ->
        loaded += url
        route(url)
      }
    }
  }

  private fun agenda(vararg slugs: String) =
    slugs.joinToString("") { """<a href="/fr/events/$it?organizer=1">Night $it</a>""" }

  private fun eventPage(slug: String) =
    fixture("shotgun/event-page.html")
      .replace("Dusk: P.E.A.R.L. B2b Not A Headliner, Againstme (Live) &More", "Night $slug")
      .replace("2025-07-26T21:59:00.000Z", starts.getValue(slug))
      .replace(Regex(""""endDate": "[^"]*","""), "")

  private fun browser(
    agendaSlugs: List<String> = listOf("a", "b"),
    route: (String) -> FetchResult? = { null },
  ) = FakeBrowser { url ->
    route(url)
      ?: when (url) {
        SHOTGUN_AGENDA_URL -> FetchResult.Ok(agenda(*agendaSlugs.toTypedArray()))
        else -> FetchResult.Ok(eventPage(url.substringAfterLast('/')))
      }
  }

  private fun url(slug: String) = ShotgunAgendaParser.eventUrl(slug)

  private suspend fun storedTitles() = store.page(Revision.ZERO, 100).events.map { it.title }

  @Test
  fun `stores every linked event with its lineup`() = runTest {
    val report = ShotgunScraper(browser(), store, clock).scrapeAgenda()

    report.failed.shouldBeNull()
    report.pages shouldBe 3
    report.listings shouldBe 2
    report.upserted shouldBe 2
    report.lineups shouldBe 2
    val events = store.page(Revision.ZERO, 100).events
    events.map { it.title } shouldContainExactlyInAnyOrder listOf("Night a", "Night b")
    events.flatMap { it.listings }.map { it.source }.distinct() shouldBe listOf(Source.SHOTGUN)
    events.first().lineup.first() shouldBe "AgainstMe"
  }

  @Test
  fun `the browser is opened once per run`() = runTest {
    val browser = browser()

    ShotgunScraper(browser, store, clock).scrapeAgenda()

    browser.sessions shouldBe 1
    browser.loaded shouldContainExactly listOf(SHOTGUN_AGENDA_URL, url("a"), url("b"))
  }

  @Test
  fun `a bot checkpoint fails the run and stores nothing`() = runTest {
    val browser = browser {
      if (it == SHOTGUN_AGENDA_URL) FetchResult.Ok(fixture("shotgun/checkpoint.html")) else null
    }

    val report = ShotgunScraper(browser, store, clock).scrapeAgenda()

    report.failed!! shouldContain "no event links"
    browser.loaded shouldContainExactly listOf(SHOTGUN_AGENDA_URL)
    storedTitles().shouldBeEmpty()
  }

  @Test
  fun `a throttled agenda fails the run`() = runTest {
    val browser = browser { FetchResult.Failed(429, "Too Many Requests") }

    val report = ShotgunScraper(browser, store, clock).scrapeAgenda()

    report.failed!! shouldContain "429"
    report.pages shouldBe 0
  }

  @Test
  fun `a throttled event page stops the run and keeps what was stored`() = runTest {
    val browser =
      browser(listOf("a", "b", "c")) {
        if (it == url("b")) FetchResult.Failed(429, "Too Many Requests") else null
      }

    val report = ShotgunScraper(browser, store, clock).scrapeAgenda()

    report.failed!! shouldContain "429"
    storedTitles() shouldContainExactly listOf("Night a")
    browser.loaded.contains(url("c")) shouldBe false
  }

  @Test
  fun `an event page that times out is retried once`() = runTest {
    var timeouts = 0
    val browser = browser {
      if (it == url("a") && timeouts++ == 0) FetchResult.Failed(null, "timeout") else null
    }

    val report = ShotgunScraper(browser, store, clock).scrapeAgenda()

    report.failed.shouldBeNull()
    browser.loaded.count { it == url("a") } shouldBe 2
    storedTitles() shouldContainExactlyInAnyOrder listOf("Night a", "Night b")
  }

  @Test
  fun `an event page without event data is skipped`() = runTest {
    val browser = browser {
      if (it == url("b")) FetchResult.Ok(fixture("shotgun/event-not-found.html")) else null
    }

    val report = ShotgunScraper(browser, store, clock).scrapeAgenda()

    report.failed.shouldBeNull()
    storedTitles() shouldContainExactly listOf("Night a")
  }

  @Test
  fun `a run that reads no event page fails`() = runTest {
    val browser = browser {
      if (it == SHOTGUN_AGENDA_URL) null else FetchResult.Failed(404, "Not Found")
    }

    val report = ShotgunScraper(browser, store, clock).scrapeAgenda()

    report.failed!! shouldContain "no event page"
  }

  @Test
  fun `events past the horizon are read but not stored`() = runTest {
    val report =
      ShotgunScraper(browser(listOf("a", "c")), store, clock, horizonDays = 2).scrapeAgenda()

    report.failed.shouldBeNull()
    report.pages shouldBe 3
    storedTitles() shouldContainExactly listOf("Night a")
  }

  @Test
  fun `events gone from the agenda are removed on the days it covers`() = runTest {
    ShotgunScraper(browser(listOf("a", "b", "c")), store, clock).scrapeAgenda()
    val removedNight = store.page(Revision.ZERO, 100).events.single { it.title == "Night b" }.id

    val report = ShotgunScraper(browser(listOf("a", "c")), store, clock).scrapeAgenda()

    report.gone shouldBe 1
    report.lastDay shouldBe LocalDate(2026, 11, 9)
    store.page(Revision.ZERO, 100).removed shouldContainExactly listOf(removedNight)
  }

  @Test
  fun `an event on the last day the agenda reached is kept`() = runTest {
    ShotgunScraper(browser(listOf("a", "b")), store, clock).scrapeAgenda()

    val report = ShotgunScraper(browser(listOf("a")), store, clock).scrapeAgenda()

    report.gone shouldBe 0
    store.page(Revision.ZERO, 100).removed.shouldBeEmpty()
  }

  @Test
  fun `refresh rereads only the Shotgun events starting soon`() = runTest {
    ShotgunScraper(browser(listOf("a", "c")), store, clock).scrapeAgenda()
    store.upsert(
      ScrapedListing(
        Source.DICE,
        "dice-soon",
        "https://dice.fm/event/dice-soon",
        "Dice night",
        Instant.parse("2026-11-07T23:00:00Z"),
        null,
        "Rex Club",
        null,
        ListingStatus.ON_SALE,
        null,
        emptyList(),
      )
    )
    val browser = browser()

    val report = ShotgunScraper(browser, store, clock).refreshSoon()

    report.failed.shouldBeNull()
    report.lineups shouldBe 1
    browser.loaded shouldContainExactly listOf(url("a"))
  }

  @Test
  fun `refresh with no Shotgun event soon opens no browser`() = runTest {
    val browser = browser()

    ShotgunScraper(browser, store, clock).refreshSoon()

    browser.sessions shouldBe 0
  }
}
