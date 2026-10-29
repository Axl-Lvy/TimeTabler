package fr.axllvy.timetabler.server

import co.touchlab.kermit.Logger
import dev.inmo.krontab.doInfinityTz
import fr.axllvy.timetabler.domain.PARIS
import fr.axllvy.timetabler.server.dice.DiceScraper
import fr.axllvy.timetabler.server.events.EventStore
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

private val log = Logger.withTag("Scheduling")

/**
 * Runs the DICE agenda scrape daily at 03:00 UTC followed by archiving, refreshes events starting
 * soon every 3 hours, and scrapes once at boot when the database is empty.
 */
fun Application.schedulingModule(
  scraper: DiceScraper,
  store: EventStore,
  clock: Clock = Clock.System,
) {
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  monitor.subscribe(ApplicationStopping) { scope.cancel() }
  // One scrape at a time, so the boot run and a scheduled run never fetch in parallel.
  val runs = Mutex()

  suspend fun guarded(name: String, block: suspend () -> Unit) {
    // krontab does not catch: an escaping exception would end every future run of this job.
    try {
      runs.withLock { block() }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      log.e(e) { "$name failed, retrying at the next run" }
    }
  }

  suspend fun agenda() {
    val report = scraper.scrapeAgenda()
    log.i { "DICE agenda: $report" }
    val yesterday = clock.now().toLocalDateTime(PARIS).date.minus(DatePeriod(days = 1))
    log.i { "Archived ${store.archiveBefore(yesterday)} past events" }
  }

  scope.launch { if (store.isEmpty()) guarded("Boot scrape") { agenda() } }
  // Seconds come first, then minutes and hours; "0o" pins the schedule to UTC.
  scope.launch { doInfinityTz("0 0 3 * * 0o") { guarded("Daily DICE scrape") { agenda() } } }
  scope.launch {
    doInfinityTz("0 30 */3 * * 0o") {
      guarded("DICE refresh") { log.i { "DICE refresh: ${scraper.refreshSoon()}" } }
    }
  }
}
