package fr.axllvy.timetabler.e2e

import androidx.room.Room
import fr.axllvy.timetabler.data.EventSync
import fr.axllvy.timetabler.data.EventsApi
import fr.axllvy.timetabler.data.SyncOutcome
import fr.axllvy.timetabler.data.room.RoomEventStore
import fr.axllvy.timetabler.data.room.TimeTablerDatabase
import fr.axllvy.timetabler.data.room.buildDatabase
import fr.axllvy.timetabler.domain.Revision
import fr.axllvy.timetabler.server.TestServer
import fr.axllvy.timetabler.server.events.ScrapedListing
import fr.axllvy.timetabler.wire.ListingStatus
import fr.axllvy.timetabler.wire.Source
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

class SyncE2eTest {

  private val server = TestServer()

  @AfterTest
  fun stop() {
    server.close()
  }

  private fun listing(id: String, startsAt: Instant) =
    ScrapedListing(
      Source.DICE,
      id,
      "https://dice.fm/event/$id",
      "Night $id",
      startsAt,
      startsAt + 7.hours,
      "Rex Club",
      "5 Boulevard Poissonnière",
      ListingStatus.ON_SALE,
      null,
      emptyList(),
    )

  @Test
  fun `a client syncs every event the server stored`(): Unit = runBlocking {
    (1..7).forEach {
      server.store.upsert(listing("e$it", Instant.parse("2026-11-0${it}T22:00:00Z")))
    }
    server.store.setLineup(Source.DICE, "e3", listOf("Laurent Garnier", "DJ Deep"))
    server.store.archiveBefore(LocalDate(2026, 11, 2))
    val local = RoomEventStore(buildDatabase(Room.inMemoryDatabaseBuilder<TimeTablerDatabase>()))
    val sync = EventSync(EventsApi(HttpClient(CIO), server.baseUrl), local)

    val outcome = sync.syncOnce()

    outcome shouldBe SyncOutcome.Ok
    val expected = server.store.page(Revision.ZERO, 2000).events
    local.events().first().sortedBy { it.id.value } shouldBe expected.sortedBy { it.id.value }
    local.lastRevision() shouldBe server.store.page(Revision.ZERO, 2000).next
  }

  @Test
  fun `a client that missed a purged removal drops the event`(): Unit = runBlocking {
    server.store.upsert(listing("old", Instant.parse("2026-11-01T22:00:00Z")))
    server.store.upsert(listing("kept", Instant.parse("2026-11-05T22:00:00Z")))
    val local = RoomEventStore(buildDatabase(Room.inMemoryDatabaseBuilder<TimeTablerDatabase>()))
    val sync = EventSync(EventsApi(HttpClient(CIO), server.baseUrl), local)
    sync.syncOnce()
    server.store.archiveBefore(LocalDate(2026, 11, 2))
    server.store.purgeRemovedBefore(Clock.System.now() + 1.seconds)
    server.store.upsert(listing("added", Instant.parse("2026-11-06T22:00:00Z")))

    val outcome = sync.syncOnce()

    outcome shouldBe SyncOutcome.Ok
    local.events().first().map { it.title }.sorted() shouldBe listOf("Night added", "Night kept")
  }

  @Test
  fun `a listing gone from its source disappears from the client`(): Unit = runBlocking {
    server.store.upsert(listing("kept", Instant.parse("2026-11-05T22:00:00Z")))
    server.store.upsert(listing("gone", Instant.parse("2026-11-05T23:00:00Z")))
    val local = RoomEventStore(buildDatabase(Room.inMemoryDatabaseBuilder<TimeTablerDatabase>()))
    val sync = EventSync(EventsApi(HttpClient(CIO), server.baseUrl), local)
    sync.syncOnce()
    server.store.markGone(
      Source.DICE,
      setOf("kept"),
      setOf(LocalDate(2026, 11, 5), LocalDate(2026, 11, 6)),
      startingFrom = Instant.parse("2026-01-01T00:00:00Z"),
    )

    val outcome = sync.syncOnce()

    outcome shouldBe SyncOutcome.Ok
    local.events().first().map { it.title } shouldBe listOf("Night kept")
  }
}
