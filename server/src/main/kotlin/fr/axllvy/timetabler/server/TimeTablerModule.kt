package fr.axllvy.timetabler.server

import fr.axllvy.timetabler.server.events.EventStore
import fr.axllvy.timetabler.server.posters.PosterCache
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
 * Installs the event feed, poster and health routes, plus the web bundle when [staticDir] is set.
 */
fun Application.timeTablerModule(store: EventStore, posters: PosterCache, staticDir: File?) {
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
  routing {
    healthRoute(store)
    eventRoutes(store)
    posterRoutes(store, posters)
    staticDir?.let { staticFrontendRoutes(it) }
  }
}
