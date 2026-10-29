package fr.axllvy.timetabler.server.routes

import fr.axllvy.timetabler.domain.EventId
import fr.axllvy.timetabler.server.events.EventStore
import fr.axllvy.timetabler.server.posters.PosterCache
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.cacheControl
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val POSTER_MAX_AGE_SECONDS = 7 * 24 * 3600

/** `GET /v1/posters/{id}`: the cached poster of an event as JPEG. */
fun Route.posterRoutes(store: EventStore, posters: PosterCache) {
  get("/v1/posters/{id}") {
    val id = EventId(call.parameters["id"].orEmpty())
    val jpeg = store.posterUrl(id)?.let { posters.jpeg(id, it) }
    if (jpeg == null) {
      call.respond(HttpStatusCode.NotFound)
      return@get
    }
    call.response.cacheControl(CacheControl.MaxAge(POSTER_MAX_AGE_SECONDS))
    call.respondBytes(jpeg, ContentType.Image.JPEG)
  }
}
