package fr.axllvy.timetabler.server

import dev.inmo.krontab.doInfinityTz
import fr.axllvy.timetabler.server.events.EventStore
import fr.axllvy.timetabler.server.health.ScrapeRuns
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Reads the saved scrape runs, then runs the DICE agenda daily at 03:00 UTC and the Shotgun agenda
 * at 04:00 UTC, the refresh jobs every 3 hours, and the DICE agenda once at boot when the database
 * is empty.
 */
fun Application.schedulingModule(jobs: ScrapeJobs, runs: ScrapeRuns, store: EventStore) {
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  monitor.subscribe(ApplicationStopping) { scope.cancel() }
  scope.launch {
    runs.load()
    if (store.isEmpty()) jobs.agenda()
  }
  // Seconds come first, then minutes and hours; "0o" pins the schedule to UTC.
  scope.launch { doInfinityTz("0 0 3 * * 0o") { jobs.agenda() } }
  scope.launch { doInfinityTz("0 30 */3 * * 0o") { jobs.refresh() } }
  // The jobs run one at a time, so Shotgun waits an hour to leave DICE its usual slot.
  scope.launch { doInfinityTz("0 0 4 * * 0o") { jobs.shotgunAgenda() } }
  scope.launch { doInfinityTz("0 45 */3 * * 0o") { jobs.shotgunRefresh() } }
}
