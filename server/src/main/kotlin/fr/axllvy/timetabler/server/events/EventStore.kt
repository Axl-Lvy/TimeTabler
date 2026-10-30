package fr.axllvy.timetabler.server.events

import fr.axllvy.timetabler.domain.EventId
import fr.axllvy.timetabler.domain.Revision
import fr.axllvy.timetabler.domain.nightDateOf
import fr.axllvy.timetabler.domain.normalizeText
import fr.axllvy.timetabler.server.db.transaction
import fr.axllvy.timetabler.wire.EventDto
import fr.axllvy.timetabler.wire.EventsPage
import fr.axllvy.timetabler.wire.ListingDto
import fr.axllvy.timetabler.wire.ListingStatus
import fr.axllvy.timetabler.wire.Source
import fr.axllvy.timetabler.wire.VenueDto
import java.security.MessageDigest
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate

/** Identity of one source listing, enough to fetch and update it. */
data class ListingRef(val source: Source, val externalId: String, val url: String)

/**
 * Result of [EventStore.upsert]: the event the listing belongs to, and whether anything changed.
 */
data class Upserted(val id: EventId, val changed: Boolean)

/** Postgres store of events and their listings; every change gives the event a new revision. */
class EventStore(private val dataSource: DataSource, private val clock: Clock = Clock.System) {

  /**
   * Inserts or updates [listing] and its event. A non-empty lineup on [listing] replaces the
   * event's.
   */
  suspend fun upsert(listing: ScrapedListing): Upserted = dataSource.transaction { c ->
    c.lockWrites()
    val venueId = c.upsertVenue(listing.venueName, listing.venueAddress)
    val hash = listing.contentHash()
    val existing =
      c.query(
          "SELECT event_id, content_hash FROM listing WHERE source = ? AND external_id = ?",
          listing.source.name,
          listing.externalId,
        ) {
          it.getObject(1, UUID::class.java) to it.getString(2)
        }
        .firstOrNull()
    when {
      existing == null -> {
        val eventId = UUID.randomUUID()
        c.update(
          """INSERT INTO event (id, revision, night_date, starts_at, ends_at, venue_id, title, poster_url, lineup)
               VALUES (?, nextval('event_revision_seq'), ?, ?, ?, ?, ?, ?, ?)""",
          eventId,
          nightDateOf(listing.startsAt).toJavaLocalDate(),
          listing.startsAt.ts(),
          listing.endsAt?.ts(),
          venueId,
          listing.title,
          listing.posterUrl,
          c.createArrayOf("text", listing.lineup.toTypedArray()),
        )
        c.update(
          """INSERT INTO listing (id, event_id, source, external_id, url, raw_title, starts_at, ends_at, status,
                 poster_url, lineup, content_hash, last_seen_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
          UUID.randomUUID(),
          eventId,
          listing.source.name,
          listing.externalId,
          listing.url,
          listing.title,
          listing.startsAt.ts(),
          listing.endsAt?.ts(),
          listing.status.name,
          listing.posterUrl,
          c.createArrayOf("text", listing.lineup.toTypedArray()),
          hash,
          clock.now().ts(),
        )
        Upserted(EventId(eventId.toString()), changed = true)
      }
      existing.second == hash -> {
        c.update(
          "UPDATE listing SET last_seen_at = ? WHERE source = ? AND external_id = ?",
          clock.now().ts(),
          listing.source.name,
          listing.externalId,
        )
        Upserted(EventId(existing.first.toString()), changed = false)
      }
      else -> {
        val eventId = existing.first
        c.update(
          """UPDATE listing SET url = ?, raw_title = ?, starts_at = ?, ends_at = ?, status = ?, poster_url = ?,
                 content_hash = ?, last_seen_at = ? WHERE source = ? AND external_id = ?""",
          listing.url,
          listing.title,
          listing.startsAt.ts(),
          listing.endsAt?.ts(),
          listing.status.name,
          listing.posterUrl,
          hash,
          clock.now().ts(),
          listing.source.name,
          listing.externalId,
        )
        c.update(
          """UPDATE event SET revision = nextval('event_revision_seq'), night_date = ?, starts_at = ?, ends_at = ?,
                 venue_id = ?, title = ?, poster_url = ?, archived = false WHERE id = ?""",
          nightDateOf(listing.startsAt).toJavaLocalDate(),
          listing.startsAt.ts(),
          listing.endsAt?.ts(),
          venueId,
          listing.title,
          listing.posterUrl,
          eventId,
        )
        if (listing.lineup.isNotEmpty())
          c.writeLineup(listing.source, listing.externalId, listing.lineup)
        Upserted(EventId(eventId.toString()), changed = true)
      }
    }
  }

  /**
   * Sets the lineup of one listing, giving its event a new revision only when the lineup differs.
   */
  suspend fun setLineup(source: Source, externalId: String, lineup: List<String>) {
    dataSource.transaction { c ->
      c.lockWrites()
      c.writeLineup(source, externalId, lineup)
    }
  }

  /** Events changed after [since], in revision order, at most [limit] of them. */
  suspend fun page(since: Revision, limit: Int): EventsPage = dataSource.transaction { c ->
    val rows =
      c.query(
        """SELECT e.id, e.revision, e.title, e.night_date, e.starts_at, e.ends_at, e.poster_url, e.lineup,
               e.archived, v.name, v.address
             FROM event e JOIN venue v ON v.id = e.venue_id
             WHERE e.revision > ? ORDER BY e.revision LIMIT ?""",
        since.value,
        limit + 1,
      ) {
        it.toEventRow()
      }
    val pageRows = rows.take(limit)
    val live = pageRows.filterNot { it.archived }
    val listings = c.listingsOf(live.map { it.id })
    EventsPage(
      events = live.map { it.toDto(listings[it.id].orEmpty()) },
      removed = pageRows.filter { it.archived }.map { EventId(it.id.toString()) },
      next = pageRows.lastOrNull()?.revision ?: since,
      hasMore = rows.size > limit,
      epoch =
        c.query("SELECT value FROM server_meta WHERE key = 'epoch'") { it.getString(1) }.single(),
    )
  }

  /**
   * Archives every live event whose night is before [nightDate]; returns how many were archived.
   */
  suspend fun archiveBefore(nightDate: LocalDate): Int = dataSource.transaction { c ->
    c.lockWrites()
    c.update(
      """UPDATE event SET archived = true, revision = nextval('event_revision_seq')
           WHERE night_date < ? AND NOT archived""",
      nightDate.toJavaLocalDate(),
    )
  }

  /** Listings of live events starting between now and [window] from now. */
  suspend fun listingsStartingWithin(window: Duration): List<ListingRef> =
    dataSource.transaction { c ->
      val now = clock.now()
      c.query(
        """SELECT l.source, l.external_id, l.url FROM listing l JOIN event e ON e.id = l.event_id
           WHERE NOT e.archived AND e.starts_at >= ? AND e.starts_at <= ? ORDER BY e.starts_at""",
        now.ts(),
        (now + window).ts(),
      ) {
        ListingRef(Source.valueOf(it.getString(1)), it.getString(2), it.getString(3))
      }
    }

  /** Listings of live events whose lineup has never been read. */
  suspend fun listingsNeedingLineup(): List<ListingRef> = dataSource.transaction { c ->
    c.query(
      """SELECT l.source, l.external_id, l.url FROM listing l JOIN event e ON e.id = l.event_id
           WHERE NOT e.archived AND l.lineup_checked_at IS NULL ORDER BY e.starts_at"""
    ) {
      ListingRef(Source.valueOf(it.getString(1)), it.getString(2), it.getString(3))
    }
  }

  /** Source URL of [id]'s poster, or null when the event is unknown or has none. */
  suspend fun posterUrl(id: EventId): String? {
    val uuid = runCatching { UUID.fromString(id.value) }.getOrNull() ?: return null
    return dataSource.transaction { c ->
      c.query("SELECT poster_url FROM event WHERE id = ?", uuid) { it.getString(1) }.firstOrNull()
    }
  }

  /** Whether no event has been stored yet. */
  suspend fun isEmpty(): Boolean = dataSource.transaction { c ->
    c.query("SELECT 1 FROM event LIMIT 1") { true }.isEmpty()
  }

  private class EventRow(
    val id: UUID,
    val revision: Revision,
    val title: String,
    val nightDate: LocalDate,
    val startsAt: Instant,
    val endsAt: Instant?,
    val posterUrl: String?,
    val lineup: List<String>,
    val archived: Boolean,
    val venue: VenueDto,
  ) {
    fun toDto(listings: List<ListingDto>) =
      EventDto(
        id = EventId(id.toString()),
        revision = revision,
        title = title,
        nightDate = nightDate,
        startsAt = startsAt,
        endsAt = endsAt,
        venue = venue,
        lineup = lineup,
        posterPath = posterUrl?.let { "/v1/posters/$id" },
        listings = listings,
      )
  }

  private fun ResultSet.toEventRow() =
    EventRow(
      id = getObject(1, UUID::class.java),
      revision = Revision(getLong(2)),
      title = getString(3),
      nightDate = getDate(4).toLocalDate().toKotlinLocalDate(),
      startsAt = getTimestamp(5).toInstant().toKotlinInstant(),
      endsAt = getTimestamp(6)?.toInstant()?.toKotlinInstant(),
      posterUrl = getString(7),
      lineup = (getArray(8).array as Array<*>).map { it as String },
      archived = getBoolean(9),
      venue = VenueDto(getString(10), getString(11)),
    )

  private fun Connection.listingsOf(eventIds: List<UUID>): Map<UUID, List<ListingDto>> {
    if (eventIds.isEmpty()) return emptyMap()
    return query(
        "SELECT event_id, source, url, status FROM listing WHERE event_id = ANY(?) ORDER BY source",
        createArrayOf("uuid", eventIds.toTypedArray()),
      ) {
        it.getObject(1, UUID::class.java) to
          ListingDto(
            Source.valueOf(it.getString(2)),
            it.getString(3),
            ListingStatus.valueOf(it.getString(4)),
          )
      }
      .groupBy({ it.first }, { it.second })
  }

  private fun Connection.writeLineup(source: Source, externalId: String, lineup: List<String>) {
    val eventId =
      query(
          """UPDATE listing SET lineup = ?, lineup_checked_at = ? WHERE source = ? AND external_id = ?
             RETURNING event_id""",
          createArrayOf("text", lineup.toTypedArray()),
          clock.now().ts(),
          source.name,
          externalId,
        ) {
          it.getObject(1, UUID::class.java)
        }
        .firstOrNull() ?: return
    update(
      """UPDATE event SET lineup = ?, revision = nextval('event_revision_seq')
         WHERE id = ? AND lineup IS DISTINCT FROM ?""",
      createArrayOf("text", lineup.toTypedArray()),
      eventId,
      createArrayOf("text", lineup.toTypedArray()),
    )
  }

  private fun Connection.upsertVenue(name: String, address: String?): UUID =
    query(
        """INSERT INTO venue (id, name, normalized_name, address) VALUES (?, ?, ?, ?)
           ON CONFLICT (normalized_name) DO UPDATE SET address = COALESCE(EXCLUDED.address, venue.address)
           RETURNING id""",
        UUID.randomUUID(),
        name,
        normalizeText(name),
        address,
      ) {
        it.getObject(1, UUID::class.java)
      }
      .single()

  // Serialises writers so revisions commit in sequence order and a paging client never skips one.
  private fun Connection.lockWrites() {
    query("SELECT pg_advisory_xact_lock(7271)") {}
  }
}

private fun Instant.ts(): Timestamp = Timestamp.from(toJavaInstant())

private fun ScrapedListing.contentHash(): String {
  val text =
    listOf(url, title, startsAt, endsAt, venueName, venueAddress, status, posterUrl)
      .joinToString("\u0000")
  return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") {
    "%02x".format(it)
  }
}

private fun Connection.bind(sql: String, args: Array<out Any?>) =
  prepareStatement(sql).apply { args.forEachIndexed { i, arg -> setObject(i + 1, arg) } }

private fun Connection.update(sql: String, vararg args: Any?): Int =
  bind(sql, args).use { it.executeUpdate() }

private fun <T> Connection.query(sql: String, vararg args: Any?, row: (ResultSet) -> T): List<T> =
  bind(sql, args).use { statement ->
    statement.executeQuery().use { rs -> buildList { while (rs.next()) add(row(rs)) } }
  }
