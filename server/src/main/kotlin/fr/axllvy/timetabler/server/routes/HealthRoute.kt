package fr.axllvy.timetabler.server.routes

import co.touchlab.kermit.Logger
import fr.axllvy.timetabler.server.BUILD_SHA
import fr.axllvy.timetabler.server.events.EventStore
import fr.axllvy.timetabler.server.health.Health
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlin.coroutines.cancellation.CancellationException

private val log = Logger.withTag("Health")

/**
 * `GET /health`: answers 200 while the server runs, with the last run of each scrape job. A stale
 * or failed scrape never fails the check, so the container is not restarted for it.
 */
fun Route.healthRoute(store: EventStore) {
  get("/health") {
    val scrapes =
      try {
        store.scrapeRuns()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        log.w { "Scrape runs unreadable: ${e.message}" }
        null
      }
    call.respond(Health("ok", BUILD_SHA, scrapes))
  }
}
