package fr.axllvy.timetabler.server.events

import fr.axllvy.timetabler.wire.ListingStatus
import fr.axllvy.timetabler.wire.Source
import kotlin.time.Instant

/** One event as a source page lists it, before it is matched to a stored event. */
data class ScrapedListing(
  val source: Source,
  val externalId: String,
  val url: String,
  val title: String,
  val startsAt: Instant,
  val endsAt: Instant?,
  val venueName: String,
  val venueAddress: String?,
  val status: ListingStatus,
  val posterUrl: String?,
  val lineup: List<String>,
)
