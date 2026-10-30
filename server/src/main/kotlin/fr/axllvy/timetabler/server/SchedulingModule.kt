package fr.axllvy.timetabler.server

import dev.inmo.krontab.doInfinityTz
import fr.axllvy.timetabler.server.events.EventStore
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Runs the agenda job daily at 03:00 UTC and the refresh job every 3 hours, plus the agenda once at
 * boot when the database is empty.
 */
fun Application.schedulingModule(jobs: ScrapeJobs, store: EventStore) {
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  monitor.subscribe(ApplicationStopping) { scope.cancel() }
  scope.launch { if (store.isEmpty()) jobs.agenda() }
  // Seconds come first, then minutes and hours; "0o" pins the schedule to UTC.
  scope.launch { doInfinityTz("0 0 3 * * 0o") { jobs.agenda() } }
  scope.launch { doInfinityTz("0 30 */3 * * 0o") { jobs.refresh() } }
}
