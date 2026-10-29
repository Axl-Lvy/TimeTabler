package fr.axllvy.timetabler.server.routes

import fr.axllvy.timetabler.domain.Revision
import fr.axllvy.timetabler.server.events.EventStore
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val DEFAULT_LIMIT = 500
private const val MAX_LIMIT = 2000

/** The page size from a `limit` parameter: [default] when absent, clamped to [max], null when invalid. */
fun parsePageLimit(raw: String?, default: Int, max: Int): Int? {
  if (raw == null) return default
  val limit = raw.toIntOrNull()?.takeIf { it > 0 } ?: return null
  return minOf(limit, max)
}

/** `GET /v1/events?since=&limit=`: the event feed after a revision. */
fun Route.eventRoutes(store: EventStore) {
  get("/v1/events") {
    val params = call.request.queryParameters
    val since = params["since"]?.let { it.toLongOrNull() ?: -1 } ?: 0
    val limit = parsePageLimit(params["limit"], DEFAULT_LIMIT, MAX_LIMIT)
    if (since < 0 || limit == null) {
      call.respondText("since must be a revision and limit a positive integer", status = HttpStatusCode.BadRequest)
      return@get
    }
    call.respond(store.page(Revision(since), limit))
  }
}
