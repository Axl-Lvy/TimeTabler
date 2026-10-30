package fr.axllvy.timetabler.nav

import fr.axllvy.timetabler.domain.EventId

/** Navigation route of the event list. */
const val EVENTS_ROUTE = "events"

/** Navigation route pattern of one event, with its id as the `id` argument. */
const val EVENT_ROUTE = "event/{id}"

private val EVENT_WEB_PATH = Regex("/event/([^/]+)/?")

/** Navigation route showing [id]. */
fun eventRoute(id: EventId): String = "event/${id.value}"

/** Path of the web page showing [id]; the server answers it with the app shell. */
fun eventWebPath(id: EventId): String = "/event/${id.value}"

/** Event the web page at [path] shows, or null when it is not an event page. */
fun eventIdOfWebPath(path: String): EventId? =
  EVENT_WEB_PATH.matchEntire(path)?.groupValues?.get(1)?.let(::EventId)
