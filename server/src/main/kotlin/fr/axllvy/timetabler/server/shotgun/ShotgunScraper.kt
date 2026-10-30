package fr.axllvy.timetabler.server.shotgun

import co.touchlab.kermit.Logger
import fr.axllvy.timetabler.domain.PARIS
import fr.axllvy.timetabler.server.dice.ScrapeReport
import fr.axllvy.timetabler.server.events.EventStore
import fr.axllvy.timetabler.server.http.FetchResult
import fr.axllvy.timetabler.server.http.PageBrowser
import fr.axllvy.timetabler.server.http.PageLoader
import fr.axllvy.timetabler.wire.Source
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

private val RETRY_PAUSE = 10.seconds
private const val HTTP_TOO_MANY_REQUESTS = 429

/** Scrapes Shotgun's Paris agenda into the [EventStore] through a headless browser. */
class ShotgunScraper(
  private val browser: PageBrowser,
  private val store: EventStore,
  private val clock: Clock = Clock.System,
  private val horizonDays: Int = 60,
) {

  private val log = Logger.withTag("ShotgunScraper")

  private class Tally {
    var pages = 0
    var events = 0
    var upserted = 0
    var lineups = 0
    var listings = 0
    var lastDay: LocalDate? = null
    var gone = 0
    var failed: String? = null
    // Start day of the latest event read, even past the horizon.
    var reached: LocalDate? = null

    fun report() =
      ScrapeReport(
        pages = pages,
        upserted = upserted,
        failed = failed,
        lineups = lineups,
        listings = listings,
        lastDay = lastDay,
        gone = gone,
      )
  }

  /**
   * Reads the agenda, then every event page it links to, storing the events up to [horizonDays]
   * ahead with their lineups. A failed agenda or one without events stops the run, and a throttled
   * event page stops the rest. A run that read every page it could marks gone the listings the
   * agenda no longer links, on days before the last one it reached.
   */
  suspend fun scrapeAgenda(): ScrapeReport = browser.session { loader ->
    val startedAt = clock.now()
    val today = startedAt.toLocalDateTime(PARIS).date
    val tally = Tally()
    val agenda =
      when (val result = fetchRetryingTimeouts(loader, SHOTGUN_AGENDA_URL)) {
        is FetchResult.Ok -> result.body
        is FetchResult.Failed -> {
          tally.failed = "$SHOTGUN_AGENDA_URL: ${result.status ?: "no response"} ${result.reason}"
          log.w { "Stopping Shotgun agenda scrape at ${tally.failed}" }
          return@session tally.report()
        }
      }
    tally.pages++
    val slugs = ShotgunAgendaParser.eventSlugs(agenda)
    tally.listings = slugs.size
    // An agenda without events is a bot challenge or a new page layout, not an empty city.
    if (slugs.isEmpty()) tally.failed = "$SHOTGUN_AGENDA_URL: no event links"
    else readEvents(loader, slugs, tally, horizon = today + DatePeriod(days = horizonDays))
    if (tally.failed == null && tally.events == 0) tally.failed = "no event page could be read"
    if (tally.failed != null) return@session tally.report()
    // The agenda may stop partway through its last day, so only the days before it are complete.
    val covered =
      generateSequence(today) { it + DatePeriod(days = 1) }
        .takeWhile { day -> tally.reached.let { it != null && day < it } }
        .toSet()
    tally.lastDay = covered.maxOrNull()
    tally.gone = store.markGone(Source.SHOTGUN, slugs.toSet(), covered, startedAt)
    tally.report()
  }

  /** Re-reads the event pages of every Shotgun listing starting in the next 48 hours. */
  suspend fun refreshSoon(): ScrapeReport {
    val slugs =
      store
        .listingsStartingWithin(48.hours)
        .filter { it.source == Source.SHOTGUN }
        .map { it.externalId }
    if (slugs.isEmpty()) return ScrapeReport(pages = 0, upserted = 0, failed = null)
    return browser.session { loader ->
      val tally = Tally()
      readEvents(loader, slugs, tally, horizon = null)
      tally.report()
    }
  }

  // Stores each slug's event that starts on or before [horizon]; a throttled page stops the rest.
  private suspend fun readEvents(
    loader: PageLoader,
    slugs: List<String>,
    tally: Tally,
    horizon: LocalDate?,
  ) {
    for (slug in slugs) {
      val url = ShotgunAgendaParser.eventUrl(slug)
      val html =
        when (val result = fetchRetryingTimeouts(loader, url)) {
          is FetchResult.Ok -> result.body
          is FetchResult.Failed -> {
            log.w { "Could not load $url: ${result.status} ${result.reason}" }
            if (result.status == HTTP_TOO_MANY_REQUESTS) {
              tally.failed = "$url: ${result.status} ${result.reason}"
              return
            }
            continue
          }
        }
      tally.pages++
      val listing = ShotgunEventParser.listing(html, slug)
      if (listing == null) {
        log.w { "No event data on $url" }
        continue
      }
      tally.events++
      val day = listing.startsAt.toLocalDateTime(PARIS).date
      if (tally.reached.let { it == null || day > it }) tally.reached = day
      if (horizon != null && day > horizon) continue
      if (store.upsert(listing).changed) tally.upserted++
      store.setLineup(Source.SHOTGUN, slug, listing.lineup)
      tally.lineups++
    }
  }

  // A load that got no response (timeout, reset) is retried once; an HTTP error is not.
  private suspend fun fetchRetryingTimeouts(loader: PageLoader, url: String): FetchResult {
    val first = loader.load(url)
    if (first !is FetchResult.Failed || first.status != null) return first
    log.w { "Retrying $url after ${first.reason}" }
    delay(RETRY_PAUSE)
    return loader.load(url)
  }
}
