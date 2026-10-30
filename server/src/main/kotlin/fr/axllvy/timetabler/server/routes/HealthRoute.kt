package fr.axllvy.timetabler.server.routes

import fr.axllvy.timetabler.server.BUILD_SHA
import fr.axllvy.timetabler.server.health.Health
import fr.axllvy.timetabler.server.health.ScrapeRuns
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.Json

// Every field is written, null or default, so a reader never has to guess a missing one.
private val HEALTH_JSON = Json { encodeDefaults = true }

/**
 * `GET /health`: answers 200 while the server runs, with the last run of each scrape job. It never
 * waits on the database, and a stale or failed scrape never fails the check.
 */
fun Route.healthRoute(runs: ScrapeRuns) {
  get("/health") {
    call.respondText(
      HEALTH_JSON.encodeToString(Health.serializer(), Health("ok", BUILD_SHA, runs.current())),
      ContentType.Application.Json,
    )
  }
}
