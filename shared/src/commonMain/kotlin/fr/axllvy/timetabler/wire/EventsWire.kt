package fr.axllvy.timetabler.wire

import fr.axllvy.timetabler.domain.EventId
import fr.axllvy.timetabler.domain.Revision
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Ticketing site an event listing was scraped from. */
@Serializable
enum class Source {
  DICE,
  SHOTGUN,
}

/** Sale status of a listing as its source reports it. */
@Serializable
enum class ListingStatus {
  ON_SALE,
  SOLD_OUT,
  CANCELLED,
  UNKNOWN,
}

/** Where an event takes place. */
@Serializable data class VenueDto(val name: String, val address: String?)

/** One source page selling tickets for an event. */
@Serializable
data class ListingDto(val source: Source, val url: String, val status: ListingStatus)

/** One real-world event as clients store and show it. */
@Serializable
data class EventDto(
  val id: EventId,
  val revision: Revision,
  val title: String,
  val nightDate: LocalDate,
  val startsAt: Instant,
  val endsAt: Instant?,
  val venue: VenueDto,
  val lineup: List<String>,
  val posterPath: String?,
  val listings: List<ListingDto>,
)

/** One page of the event feed: events changed after the requested revision, up to [next]. */
@Serializable
data class EventsPage(
  val events: List<EventDto>,
  val removed: List<EventId>,
  val next: Revision,
  val hasMore: Boolean,
)

/** JSON configuration shared by the server and every client for the event feed. */
val WIRE_JSON: Json = Json {
  ignoreUnknownKeys = true
  explicitNulls = false
}
