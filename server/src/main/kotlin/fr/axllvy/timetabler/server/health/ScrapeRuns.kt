package fr.axllvy.timetabler.server.health

import co.touchlab.kermit.Logger
import fr.axllvy.timetabler.server.dice.ScrapeReport
import fr.axllvy.timetabler.server.events.EventStore
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The last run of each scrape job, held in memory for `/health` and saved in the [EventStore]. */
class ScrapeRuns(private val store: EventStore) {

  private val log = Logger.withTag("ScrapeRuns")
  private val writes = Mutex()
  @Volatile private var runs: Map<String, ScrapeRun>? = null

  /** The last run of each job by name, null while the saved runs have not been read. */
  fun current(): Map<String, ScrapeRun>? = runs

  /** Reads the runs saved by earlier processes; logs and keeps none when the store fails. */
  suspend fun load() {
    writes.withLock { loadUnlocked() }
  }

  /**
   * Records that [job] ended at [at] with [report], in memory first, then in the store; throws when
   * the store write fails.
   */
  suspend fun record(job: String, report: ScrapeReport, at: Instant) {
    writes.withLock {
      if (runs == null) loadUnlocked()
      val run = runs?.get(job).after(report, at)
      runs = runs.orEmpty() + (job to run)
      store.saveScrapeRun(job, run)
    }
  }

  private suspend fun loadUnlocked() {
    try {
      runs = store.scrapeRuns()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      log.w { "Saved scrape runs unreadable: ${e.message}" }
    }
  }
}
