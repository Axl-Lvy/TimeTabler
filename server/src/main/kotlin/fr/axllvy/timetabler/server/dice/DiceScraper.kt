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
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

private val RETRY_PAUSE = 10.seconds

/** Summary of one scrape run; [failed] says why the run stopped early, null when it completed. */
data class ScrapeReport(val pages: Int, val upserted: Int, val failed: String?)

/** Scrapes DICE's Paris agenda into the [EventStore]. */
class DiceScraper(
  private val fetcher: PoliteFetcher,
  private val store: EventStore,
  private val clock: Clock = Clock.System,
  private val horizonDays: Int = 60,
) {

  private val log = Logger.withTag("DiceScraper")

  /**
   * Walks every day from today to [horizonDays] ahead in each music category, then fetches the
   * lineup of every new or changed listing. Stops at the first failed agenda page.
   */
  suspend fun scrapeAgenda(): ScrapeReport {
    val today = clock.now().toLocalDateTime(PARIS).date
    var pages = 0
    var upserted = 0
    val toFetch = linkedMapOf<String, ListingRef>()
    for (offset in 0..horizonDays) {
      val day = today + DatePeriod(days = offset)
      for (category in DICE_CATEGORIES) {
        val url = DiceBrowseParser.dayUrl(category, day)
        val body =
          when (val result = fetchRetryingTimeouts(url)) {
            is FetchResult.Ok -> result.body
            is FetchResult.Failed -> {
              val reason = "$url: ${result.status ?: "no response"} ${result.reason}"
              log.w { "Stopping DICE agenda scrape at $reason" }
              fetchLineups(toFetch.values)
              return ScrapeReport(pages, upserted, reason)
            }
          }
        pages++
        val page = DiceBrowseParser.parse(body)
        if (page.overflow) log.w { "DICE $category on $day lists more events than one page shows" }
        for (listing in page.listings) {
          val result = store.upsert(listing)
          if (result.changed) {
            upserted++
            toFetch[listing.externalId] = ListingRef(Source.DICE, listing.externalId, listing.url)
          }
        }
      }
    }
    fetchLineups(toFetch.values)
    return ScrapeReport(pages, upserted, null)
  }

  /** Re-reads the lineup of every listing starting in the next 48 hours. */
  suspend fun refreshSoon(): ScrapeReport {
    val refs = store.listingsStartingWithin(48.hours).filter { it.source == Source.DICE }
    fetchLineups(refs)
    return ScrapeReport(refs.size, 0, null)
  }

  // A request that got no response (timeout, reset) is retried once; an HTTP error is not.
  private suspend fun fetchRetryingTimeouts(url: String): FetchResult {
    val first = fetcher.get(url)
    if (first !is FetchResult.Failed || first.status != null) return first
    log.w { "Retrying $url after ${first.reason}" }
    delay(RETRY_PAUSE)
    return fetcher.get(url)
  }

  private suspend fun fetchLineups(refs: Collection<ListingRef>) {
    for (ref in refs) {
      when (val result = fetcher.get(ref.url)) {
        is FetchResult.Ok ->
          DiceEventParser.performers(result.body)?.let {
            store.setLineup(ref.source, ref.externalId, it)
          } ?: log.w { "No lineup data on ${ref.url}" }
        is FetchResult.Failed -> log.w { "Could not fetch ${ref.url}: ${result.status} ${result.reason}" }
      }
    }
  }
}
