package fr.axllvy.timetabler.server

import co.touchlab.kermit.Logger
import fr.axllvy.timetabler.domain.PARIS
import fr.axllvy.timetabler.server.dice.DiceScraper
import fr.axllvy.timetabler.server.dice.ScrapeReport
import fr.axllvy.timetabler.server.events.EventStore
import fr.axllvy.timetabler.server.health.ScrapeRuns
import fr.axllvy.timetabler.server.posters.PosterCache
import fr.axllvy.timetabler.server.shotgun.ShotgunScraper
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * The scheduled scrape jobs. They run one at a time, record each run in [runs] for `/health`, and
 * log failures instead of throwing. The Shotgun jobs do nothing when [shotgun] is null.
 */
class ScrapeJobs(
  private val scraper: DiceScraper,
  private val store: EventStore,
  private val posters: PosterCache,
  private val runs: ScrapeRuns,
  private val removalRetention: Duration,
  private val clock: Clock = Clock.System,
  private val shotgun: ShotgunScraper? = null,
) {

  private val log = Logger.withTag("ScrapeJobs")
  private val oneAtATime = Mutex()

  /**
   * Scrapes the DICE agenda, archives the events of nights before yesterday, then purges removals
   * older than [removalRetention].
   */
  suspend fun agenda() =
    run(AGENDA) {
      val report = scraper.scrapeAgenda()
      // Archiving runs even when the scrape failed: it only compares night dates with today, so it
      // never drops an event the failed scrape would have kept.
      val yesterday = clock.now().toLocalDateTime(PARIS).date.minus(DatePeriod(days = 1))
      log.i { "Archived ${store.archiveBefore(yesterday)} past events" }
      val purged = store.purgeRemovedBefore(clock.now() - removalRetention)
      log.i { "Purged $purged removals older than $removalRetention" }
      report
    }

  /** Re-reads the lineups of events starting in the next 48 hours and prefetches their posters. */
  suspend fun refresh() =
    run(REFRESH) {
      val report = scraper.refreshSoon()
      // Posters come from image hosts, not DICE, so a throttled refresh still fetches them.
      val fetched =
        guarded("Prefetching posters") { posters.prefetch(store.postersStartingWithin(SOON)) }
      report.copy(posters = fetched ?: 0)
    }

  /** Scrapes the Shotgun agenda and the event pages it links to. */
  suspend fun shotgunAgenda() {
    val scraper = shotgun ?: return
    run(SHOTGUN_AGENDA) { scraper.scrapeAgenda() }
  }

  /** Re-reads the Shotgun event pages of events starting in the next 48 hours. */
  suspend fun shotgunRefresh() {
    val scraper = shotgun ?: return
    run(SHOTGUN_REFRESH) { scraper.refreshSoon() }
  }

  private suspend fun run(job: String, block: suspend () -> ScrapeReport) {
    oneAtATime.withLock {
      // krontab does not catch: an escaping exception would end every future run of the job.
      val report =
        guarded("$job job") { block() }
          ?: ScrapeReport(pages = 0, upserted = 0, failed = "$job job threw, see the logs")
      log.i { "$job: $report" }
      guarded("Saving the $job run") { runs.record(job, report, clock.now()) }
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
    const val SHOTGUN_AGENDA = "shotgun-agenda"
    const val SHOTGUN_REFRESH = "shotgun-refresh"
    val SOON = 48.hours
  }
}
