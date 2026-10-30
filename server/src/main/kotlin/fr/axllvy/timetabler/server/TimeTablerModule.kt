package fr.axllvy.timetabler.server

import fr.axllvy.timetabler.server.auth.AuthConfig
import fr.axllvy.timetabler.server.auth.installAdminAuth
import fr.axllvy.timetabler.server.events.EventStore
import fr.axllvy.timetabler.server.health.ScrapeRuns
import fr.axllvy.timetabler.server.posters.PosterCache
import fr.axllvy.timetabler.server.routes.adminApiRoutes
import fr.axllvy.timetabler.server.routes.eventRoutes
import fr.axllvy.timetabler.server.routes.healthRoute
import fr.axllvy.timetabler.server.routes.posterRoutes
import fr.axllvy.timetabler.server.routes.staticFrontendRoutes
import fr.axllvy.timetabler.wire.WIRE_JSON
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.header
import io.ktor.server.routing.routing
import java.io.File

/**
 * Installs the event feed, poster, health and admin routes, plus the web bundle when [staticDir] is
 * set. Admin routes accept tokens of the tenant in [auth] and refuse every request without one.
 */
fun Application.timeTablerModule(
  store: EventStore,
  posters: PosterCache,
  scrapeRuns: ScrapeRuns,
  staticDir: File?,
  auth: AuthConfig? = null,
) {
  install(ContentNegotiation) { json(WIRE_JSON) }
  install(AutoHeadResponse)
  // The wasm runtime needs a cross-origin isolated page; credentialless keeps same-origin posters
  // working.
  install(
    createApplicationPlugin("CrossOriginIsolation") {
      onCall { call ->
        call.response.header("Cross-Origin-Opener-Policy", "same-origin")
        call.response.header("Cross-Origin-Embedder-Policy", "credentialless")
      }
    }
  )
  installAdminAuth(auth)
  routing {
    healthRoute(scrapeRuns)
    eventRoutes(store)
    posterRoutes(store, posters)
    adminApiRoutes()
    staticDir?.let { staticFrontendRoutes(it) }
  }
}
