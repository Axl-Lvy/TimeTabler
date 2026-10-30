package fr.axllvy.timetabler.data

import fr.axllvy.timetabler.domain.EventId
import fr.axllvy.timetabler.domain.Revision
import fr.axllvy.timetabler.wire.EventDto
import fr.axllvy.timetabler.wire.EventsPage
import fr.axllvy.timetabler.wire.ListingDto
import fr.axllvy.timetabler.wire.ListingStatus
import fr.axllvy.timetabler.wire.Source
import fr.axllvy.timetabler.wire.VenueDto
import fr.axllvy.timetabler.wire.WIRE_JSON
import fr.axllvy.timetabler.wire.WIRE_SCHEMA_VERSION
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/** Behaviour every [EventStoreLocal] implementation must have. */
abstract class EventStoreLocalContract {

  /** A new, empty store; each test creates at most one. */
  abstract suspend fun newStore(): EventStoreLocal

  /** Writes [json] as event [id]'s stored row, as an older app version might have. */
  abstract suspend fun writeRawRow(id: String, json: String)

  protected fun event(id: String, revision: Long, title: String = "Night $id") =
    EventDto(
      id = EventId(id),
      revision = Revision(revision),
      title = title,
      nightDate = LocalDate(2026, 10, 30),
      startsAt = Instant.parse("2026-10-30T22:30:00Z"),
      endsAt = null,
      venue = VenueDto("Rex Club", "5 Boulevard Poissonnière"),
      lineup = listOf("Laurent Garnier"),
      posterPath = "/v1/posters/$id",
      listings =
        listOf(ListingDto(Source.DICE, "https://dice.fm/event/$id", ListingStatus.ON_SALE)),
    )

  private fun page(
    vararg events: EventDto,
    removed: List<String> = emptyList(),
    next: Long,
    epoch: String = "e1",
  ) =
    EventsPage(
      events.toList(),
      removed.map(::EventId),
      Revision(next),
      hasMore = false,
      epoch = epoch,
    )

  @Test
  fun appliedEventsAreStoredWholly() = runTest {
    val store = newStore()

    store.apply(page(event("a", 1), event("b", 2), next = 2))

    store.events().first().sortedBy { it.id.value } shouldContainExactly
      listOf(event("a", 1), event("b", 2))
    store.event(EventId("a")).first() shouldBe event("a", 1)
  }

  @Test
  fun removedEventsAreDeleted() = runTest {
    val store = newStore()
    store.apply(page(event("a", 1), event("b", 2), next = 2))

    store.apply(page(removed = listOf("a"), next = 3))

    store.events().first().map { it.id.value } shouldContainExactly listOf("b")
  }

  @Test
  fun lastRevisionFollowsTheAppliedPage() = runTest {
    val store = newStore()

    store.apply(page(event("a", 1), next = 5))

    store.lastRevision() shouldBe Revision(5)
  }

  @Test
  fun hasEverSyncedOnlyAfterAPage() = runTest {
    val store = newStore()
    store.hasEverSynced() shouldBe false
    store.lastRevision() shouldBe Revision.ZERO

    store.apply(page(next = 0))

    store.hasEverSynced() shouldBe true
  }

  @Test
  fun aNewerRevisionReplacesTheEvent() = runTest {
    val store = newStore()
    store.apply(page(event("a", 1), next = 1))

    store.apply(page(event("a", 4, title = "Renamed"), next = 4))

    store.events().first().single().title shouldBe "Renamed"
  }

  @Test
  fun epochFollowsTheAppliedPage() = runTest {
    val store = newStore()
    store.epoch() shouldBe null

    store.apply(page(event("a", 1), next = 1, epoch = "e7"))

    store.epoch() shouldBe "e7"
  }

  @Test
  fun aFreshStoreHasNoSchemaVersion() = runTest {
    val store = newStore()

    val version = store.schemaVersion()

    version shouldBe null
  }

  @Test
  fun schemaVersionFollowsTheAppliedPage() = runTest {
    val store = newStore()

    store.apply(page(event("a", 1), next = 1))

    store.schemaVersion() shouldBe WIRE_SCHEMA_VERSION
  }

  @Test
  fun rowsThatNoLongerDecodeAreSkipped() = runTest {
    val store = newStore()
    store.apply(page(event("a", 1), next = 1))
    writeRawRow("partial", """{"id":"partial"}""")
    writeRawRow(
      "misdated",
      WIRE_JSON.encodeToString(EventDto.serializer(), event("misdated", 2))
        .replace("2026-10-30T22:30:00Z", "soon"),
    )

    store.apply(page(event("b", 3), next = 3))

    store.events().first().map { it.id.value }.sorted() shouldContainExactly listOf("a", "b")
  }

  @Test
  fun clearForgetsEverything() = runTest {
    val store = newStore()
    store.apply(page(event("a", 1), next = 1))

    store.clear()

    store.events().first() shouldBe emptyList()
    store.lastRevision() shouldBe Revision.ZERO
    store.hasEverSynced() shouldBe false
    store.epoch() shouldBe null
    store.schemaVersion() shouldBe null
  }
}
