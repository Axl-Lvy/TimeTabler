package fr.axllvy.timetabler.server.shotgun

import co.touchlab.kermit.Logger
import fr.axllvy.timetabler.server.dice.LENIENT_JSON
import fr.axllvy.timetabler.server.dice.get
import fr.axllvy.timetabler.server.dice.string
import fr.axllvy.timetabler.server.events.ScrapedListing
import fr.axllvy.timetabler.wire.ListingStatus
import fr.axllvy.timetabler.wire.Source
import kotlin.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import org.jsoup.Jsoup

private val EVENT_TYPES = setOf("MusicEvent", "Event", "Festival", "DanceEvent")

/** Reads a Shotgun event page from its schema.org JSON-LD. */
object ShotgunEventParser {

  private val log = Logger.withTag("ShotgunEventParser")

  /**
   * The listing of the event page with [slug], lineup included. Null when the page has no event
   * data or lacks its title, start or venue.
   */
  fun listing(html: String, slug: String): ScrapedListing? {
    val event =
      Jsoup.parse(html)
        .select("script[type=application/ld+json]")
        .mapNotNull { runCatching { LENIENT_JSON.parseToJsonElement(it.data()) }.getOrNull() }
        .flatMap { it as? JsonArray ?: listOf(it) }
        .firstOrNull { it["@type"].string in EVENT_TYPES } ?: return null
    val title = event["name"].string?.trim()?.takeIf { it.isNotEmpty() }
    val start = event["startDate"].string?.let { runCatching { Instant.parse(it) }.getOrNull() }
    val venue = event["location"].first()
    val venueName = venue["name"].string?.trim()?.takeIf { it.isNotEmpty() }
    if (title == null || start == null || venueName == null) {
      log.w { "Skipping Shotgun event $slug: missing title, start or venue" }
      return null
    }
    return ScrapedListing(
      source = Source.SHOTGUN,
      externalId = slug,
      url = ShotgunAgendaParser.eventUrl(slug),
      title = title,
      startsAt = start,
      endsAt = event["endDate"].string?.let { runCatching { Instant.parse(it) }.getOrNull() },
      venueName = venueName,
      venueAddress = venue["address"].let { it.string ?: it["streetAddress"].string },
      status = status(event),
      posterUrl = event["image"].first().let { it.string ?: it["url"].string },
      lineup =
        event["performer"]
          .all()
          .mapNotNull { it["name"].string?.trim()?.takeIf(String::isNotEmpty) }
          .distinct(),
    )
  }

  private fun status(event: JsonElement): ListingStatus {
    val offers = event["offers"].all()
    return when {
      event["eventStatus"].string?.endsWith("EventCancelled") == true -> ListingStatus.CANCELLED
      offers.isNotEmpty() &&
        offers.all { it["availability"].string?.endsWith("SoldOut") == true } ->
        ListingStatus.SOLD_OUT
      event["eventStatus"].string?.endsWith("EventScheduled") == true -> ListingStatus.ON_SALE
      else -> ListingStatus.UNKNOWN
    }
  }

  // JSON-LD allows one value or a list of them wherever a list is expected.
  private fun JsonElement?.all(): List<JsonElement> =
    when (this) {
      null -> emptyList()
      is JsonArray -> this
      else -> listOf(this)
    }

  private fun JsonElement?.first(): JsonElement? = all().firstOrNull()
}
