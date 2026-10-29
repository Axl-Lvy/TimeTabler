package fr.axllvy.timetabler.server.routes

import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** `GET /health`: answers while the server runs. */
fun Route.healthRoute() {
  get("/health") { call.respondText("ok") }
}
