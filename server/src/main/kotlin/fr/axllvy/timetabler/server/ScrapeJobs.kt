package fr.axllvy.timetabler.server

import co.touchlab.kermit.Logger
import fr.axllvy.timetabler.domain.PARIS
import fr.axllvy.timetabler.server.dice.DiceScraper
import fr.axllvy.timetabler.server.events.EventStore
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/** The scheduled scrape jobs. They run one at a time and log failures instead of throwing. */
class ScrapeJobs(
  private val scraper: DiceScraper,
  private val store: EventStore,
  private val clock: Clock = Clock.System,
) {

  private val log = Logger.withTag("ScrapeJobs")
  private val runs = Mutex()

  /** Scrapes the DICE agenda, then archives the events of nights before yesterday. */
  suspend fun agenda() =
    guarded("DICE agenda") {
      log.i { "DICE agenda: ${scraper.scrapeAgenda()}" }
      // Archiving runs even when the scrape failed: it only compares night dates with today, so it
      // never drops an event the failed scrape would have kept.
      val yesterday = clock.now().toLocalDateTime(PARIS).date.minus(DatePeriod(days = 1))
      log.i { "Archived ${store.archiveBefore(yesterday)} past events" }
    }

  /** Re-reads the lineups of events starting in the next 48 hours. */
  suspend fun refresh() =
    guarded("DICE refresh") { log.i { "DICE refresh: ${scraper.refreshSoon()}" } }

  private suspend fun guarded(name: String, block: suspend () -> Unit) {
    // krontab does not catch: an escaping exception would end every future run of the job.
    try {
      runs.withLock { block() }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      log.e(e) { "$name failed, retrying at the next run" }
    }
  }
}
