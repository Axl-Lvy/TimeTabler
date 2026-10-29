package fr.axllvy.timetabler.server.dice

import co.touchlab.kermit.Logger
import fr.axllvy.timetabler.server.events.ScrapedListing
import fr.axllvy.timetabler.wire.ListingStatus
import fr.axllvy.timetabler.wire.Source
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import org.jsoup.Jsoup

/** Music categories of DICE's Paris browse pages that the scraper walks. */
val DICE_CATEGORIES = listOf("dj", "party", "gig")

private const val PARIS_BROWSE = "https://dice.fm/browse/paris-5b23e8a0e63cc224a4c36a2d"

/** Events of one DICE browse page; [overflow] means DICE had more than the page shows. */
data class DiceBrowsePage(val listings: List<ScrapedListing>, val overflow: Boolean)

/** Reads DICE browse pages from their embedded page data. */
object DiceBrowseParser {

  private val log = Logger.withTag("DiceBrowseParser")

  /** Parses the events embedded in a browse page; events missing required fields are skipped. */
  fun parse(html: String): DiceBrowsePage {
    val props = Jsoup.parse(html).nextData()["props"]["pageProps"]
    val events = props["events"] as? JsonArray ?: JsonArray(emptyList())
    val cursor = props["nextCursor"]
    return DiceBrowsePage(
      listings = events.mapNotNull { event -> toListing(event) },
      overflow = cursor != null && cursor != JsonNull,
    )
  }

  /** URL of the browse page listing one [category]'s events on [day]. */
  fun dayUrl(category: String, day: LocalDate): String =
    "$PARIS_BROWSE/music/$category?from=$day&until=$day"

  private fun toListing(event: JsonElement): ScrapedListing? {
    val id = event["id"].string
    val title = event["name"].string?.trim()
    val start = event["dates"]["event_start_date"].string
    val venue = (event["venues"] as? JsonArray)?.firstOrNull()
    val venueName = venue["name"].string?.trim()
    if (id == null || title == null || start == null || venueName == null) {
      log.w { "Skipping DICE event ${id ?: "without id"}: missing title, start or venue" }
      return null
    }
    return ScrapedListing(
      source = Source.DICE,
      externalId = id,
      url = "https://dice.fm/event/$id",
      title = title,
      startsAt = Instant.parse(start),
      endsAt = event["dates"]["event_end_date"].string?.let(Instant::parse),
      venueName = venueName,
      venueAddress = venue["address"].string,
      status = status(event["status"].string),
      posterUrl = event["images"]["square"].string,
      lineup = emptyList(),
    )
  }

  private fun status(value: String?): ListingStatus =
    when {
      value == null -> ListingStatus.UNKNOWN
      "sold" in value -> ListingStatus.SOLD_OUT
      "cancel" in value -> ListingStatus.CANCELLED
      value == "on-sale" || value == "announced" -> ListingStatus.ON_SALE
      else -> ListingStatus.UNKNOWN
    }
}
