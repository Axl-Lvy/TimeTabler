package fr.axllvy.timetabler.wire

import fr.axllvy.timetabler.domain.EventId
import fr.axllvy.timetabler.domain.Revision
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

/**
 * Version of the event JSON clients store. Bump it whenever events stored by an older client might
 * no longer decode, so every client clears its store and syncs again.
 */
const val WIRE_SCHEMA_VERSION = 1

/** Ticketing site an event listing was scraped from; [UNKNOWN] stands for one added later. */
@Serializable(with = SourceSerializer::class)
enum class Source {
  DICE,
  SHOTGUN,
  UNKNOWN,
}

/** Sale status of a listing as its source reports it; [UNKNOWN] also stands for one added later. */
@Serializable(with = ListingStatusSerializer::class)
enum class ListingStatus {
  ON_SALE,
  SOLD_OUT,
  CANCELLED,
  UNKNOWN,
}

/** Serializes an enum by name, decoding a name this build does not know as [fallback]. */
abstract class FallbackEnumSerializer<T : Enum<T>>(
  serialName: String,
  private val values: List<T>,
  private val fallback: T,
) : KSerializer<T> {

  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(value.name)

  override fun deserialize(decoder: Decoder): T {
    val name = decoder.decodeString()
    return values.firstOrNull { it.name == name } ?: fallback
  }
}

internal object SourceSerializer :
  FallbackEnumSerializer<Source>(
    "fr.axllvy.timetabler.wire.Source",
    Source.entries,
    Source.UNKNOWN,
  )

internal object ListingStatusSerializer :
  FallbackEnumSerializer<ListingStatus>(
    "fr.axllvy.timetabler.wire.ListingStatus",
    ListingStatus.entries,
    ListingStatus.UNKNOWN,
  )

/** Where an event takes place. */
@Serializable data class VenueDto(val name: String, val address: String?)

/** One source page selling tickets for an event. */
@Serializable data class ListingDto(val source: Source, val url: String, val status: ListingStatus)

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

/**
 * One page of the event feed: events changed after the requested revision, up to [next]. [epoch]
 * names the server database; revisions from another epoch mean nothing, so a client seeing a new
 * one starts over. [purgedThrough] is the newest removal the feed no longer lists, so a client
 * behind it may have missed one and starts over too.
 */
@Serializable
data class EventsPage(
  val events: List<EventDto>,
  val removed: List<EventId>,
  val next: Revision,
  val hasMore: Boolean,
  val epoch: String = "",
  val purgedThrough: Revision = Revision.ZERO,
)

/** JSON configuration shared by the server and every client for the event feed. */
val WIRE_JSON: Json = Json {
  ignoreUnknownKeys = true
  explicitNulls = false
}
