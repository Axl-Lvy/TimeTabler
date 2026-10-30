package fr.axllvy.timetabler.server.dice

import co.touchlab.kermit.Logger
import fr.axllvy.timetabler.domain.PARIS
import fr.axllvy.timetabler.server.events.EventStore
import fr.axllvy.timetabler.server.events.ListingRef
import fr.axllvy.timetabler.server.http.FetchResult
import fr.axllvy.timetabler.server.http.PoliteFetcher
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

/**
 * Summary of one scrape run: agenda [pages] read, distinct [listings] they held, listings
 * [upserted], event pages whose [lineups] were read, the [lastDay] of the agenda read in full, and
 * [failed], why the run stopped early, null when it completed. [overflowedSubGenres] counts
 * sub-genre pages that still held more events than they show; [posters] counts posters prefetched.
 */
data class ScrapeReport(
  val pages: Int,
  val upserted: Int,
  val failed: String?,
  val overflowedDays: Int = 0,
  val overflowedSubGenres: Int = 0,
  val lineups: Int = 0,
  val listings: Int = 0,
  val lastDay: LocalDate? = null,
  val posters: Int = 0,
)

/** Scrapes DICE's Paris agenda into the [EventStore]. */
class DiceScraper(
  private val fetcher: PoliteFetcher,
  private val store: EventStore,
  private val clock: Clock = Clock.System,
  private val horizonDays: Int = 60,
) {

  private val log = Logger.withTag("DiceScraper")

  private class Tally {
    var pages = 0
    var upserted = 0
    var overflowedDays = 0
    var overflowedSubGenres = 0
    var lineups = 0
    val listings = HashSet<String>()
    var lastDay: LocalDate? = null

    fun report(failed: String?) =
      ScrapeReport(
        pages,
        upserted,
        failed,
        overflowedDays,
        overflowedSubGenres,
        lineups,
        listings.size,
        lastDay,
      )
  }

  /**
   * Walks every day from today to [horizonDays] ahead in each music category, reading a day's
   * sub-genre pages when it holds more events than one page shows, then fetches the lineup of every
   * new or changed listing and of every listing whose lineup was never read. A failed agenda page
   * stops the run without fetching lineups; a throttled lineup stops the rest and fails the run.
   */
  suspend fun scrapeAgenda(): ScrapeReport {
    val today = clock.now().toLocalDateTime(PARIS).date
    val tally = Tally()
    val toFetch = linkedMapOf<String, ListingRef>()
    for (offset in 0..horizonDays) {
      val day = today + DatePeriod(days = offset)
      for (category in DICE_CATEGORIES) {
        val page =
          when (val read = readPage(DiceBrowseParser.dayUrl(category, day), tally)) {
            is PageRead.Ok -> read.page
            is PageRead.Failed -> return stopped(tally, read.reason)
          }
        var listings = page.listings
        if (page.overflow) {
          tally.overflowedDays++
          if (page.subGenreUrls.isEmpty())
            log.w { "DICE $category on $day overflows with no sub-genres" }
          for (url in page.subGenreUrls) {
            val subPage =
              when (val read = readPage(url, tally)) {
                is PageRead.Ok -> read.page
                is PageRead.Failed -> return stopped(tally, read.reason)
              }
            if (subPage.overflow) {
              tally.overflowedSubGenres++
              log.w { "DICE $url lists more events than one page shows" }
            }
            listings = listings + subPage.listings
          }
        }
        for (listing in listings.distinctBy { it.externalId }) {
          tally.listings += listing.externalId
          val result = store.upsert(listing)
          if (result.changed) {
            tally.upserted++
            toFetch[listing.externalId] = ListingRef(Source.DICE, listing.externalId, listing.url)
          }
        }
      }
      tally.lastDay = day
    }
    val lineups = fetchLineups(withUnreadLineups(toFetch))
    tally.lineups = lineups.read
    return tally.report(lineups.stopped)
  }

  /** Re-reads the lineup of every listing starting in the next 48 hours. */
  suspend fun refreshSoon(): ScrapeReport {
    val refs = store.listingsStartingWithin(48.hours).filter { it.source == Source.DICE }
    val lineups = fetchLineups(refs)
    return ScrapeReport(pages = 0, upserted = 0, failed = lineups.stopped, lineups = lineups.read)
  }

  // Lineups are skipped too: a failing agenda usually means DICE wants us to back off.
  private fun stopped(tally: Tally, reason: String): ScrapeReport {
    log.w { "Stopping DICE agenda scrape at $reason" }
    return tally.report(reason)
  }

  private sealed interface PageRead {
    data class Ok(val page: DiceBrowsePage) : PageRead

    data class Failed(val reason: String) : PageRead
  }

  private suspend fun readPage(url: String, tally: Tally): PageRead {
    val body =
      when (val result = fetchRetryingTimeouts(url)) {
        is FetchResult.Ok -> result.body
        is FetchResult.Failed ->
          return PageRead.Failed("$url: ${result.status ?: "no response"} ${result.reason}")
      }
    tally.pages++
    return DiceBrowseParser.parse(body)?.let { PageRead.Ok(it) }
      ?: PageRead.Failed("$url: no page data")
  }

  private suspend fun withUnreadLineups(changed: Map<String, ListingRef>): Collection<ListingRef> =
    (changed.values + store.listingsNeedingLineup().filter { it.source == Source.DICE })
      .distinctBy { it.externalId }

  // A request that got no response (timeout, reset) is retried once; an HTTP error is not.
  private suspend fun fetchRetryingTimeouts(url: String): FetchResult {
    val first = fetcher.get(url)
    if (first !is FetchResult.Failed || first.status != null) return first
    log.w { "Retrying $url after ${first.reason}" }
    delay(RETRY_PAUSE)
    return fetcher.get(url)
  }

  private class LineupRun(val read: Int, val stopped: String?)

  private suspend fun fetchLineups(refs: Collection<ListingRef>): LineupRun {
    var read = 0
    for (ref in refs) {
      when (val result = fetcher.get(ref.url)) {
        is FetchResult.Ok -> {
          val lineup = DiceEventParser.performers(result.body)
          if (lineup == null) {
            log.w { "No lineup data on ${ref.url}" }
          } else {
            store.setLineup(ref.source, ref.externalId, lineup)
            read++
          }
        }
        is FetchResult.Failed -> {
          log.w { "Could not fetch ${ref.url}: ${result.status} ${result.reason}" }
          if (result.status == HTTP_TOO_MANY_REQUESTS)
            return LineupRun(read, "${ref.url}: ${result.status} ${result.reason}")
        }
      }
    }
    return LineupRun(read, null)
  }
}
