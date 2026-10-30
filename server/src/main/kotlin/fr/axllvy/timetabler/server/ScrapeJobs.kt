package fr.axllvy.timetabler.server

import co.touchlab.kermit.Logger
import fr.axllvy.timetabler.domain.PARIS
import fr.axllvy.timetabler.server.dice.DiceScraper
import fr.axllvy.timetabler.server.dice.ScrapeReport
import fr.axllvy.timetabler.server.events.EventStore
import fr.axllvy.timetabler.server.health.after
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * The scheduled scrape jobs. They run one at a time, record each run in the [EventStore] for
 * `/health`, and log failures instead of throwing.
 */
class ScrapeJobs(
  private val scraper: DiceScraper,
  private val store: EventStore,
  private val clock: Clock = Clock.System,
) {

  private val log = Logger.withTag("ScrapeJobs")
  private val runs = Mutex()

  /** Scrapes the DICE agenda, then archives the events of nights before yesterday. */
  suspend fun agenda() =
    run(AGENDA) {
      val report = scraper.scrapeAgenda()
      // Archiving runs even when the scrape failed: it only compares night dates with today, so it
      // never drops an event the failed scrape would have kept.
      val yesterday = clock.now().toLocalDateTime(PARIS).date.minus(DatePeriod(days = 1))
      log.i { "Archived ${store.archiveBefore(yesterday)} past events" }
      report
    }

  /** Re-reads the lineups of events starting in the next 48 hours. */
  suspend fun refresh() = run(REFRESH) { scraper.refreshSoon() }

  private suspend fun run(job: String, block: suspend () -> ScrapeReport) {
    runs.withLock {
      // krontab does not catch: an escaping exception would end every future run of the job.
      val report =
        guarded("$job job") { block() }
          ?: ScrapeReport(pages = 0, upserted = 0, failed = "$job job threw, see the logs")
      log.i { "$job: $report" }
      guarded("Recording the $job run") {
        store.updateScrapeRun(job) { it.after(report, clock.now()) }
      }
    }
  }

  private suspend fun <T> guarded(what: String, block: suspend () -> T): T? =
    try {
      block()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      log.e(e) { "$what failed" }
      null
    }

  private companion object {
    const val AGENDA = "agenda"
    const val REFRESH = "refresh"
  }
}
