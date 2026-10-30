package fr.axllvy.timetabler.data

import fr.axllvy.timetabler.data.room.EventDao
import fr.axllvy.timetabler.data.room.EventEntity
import fr.axllvy.timetabler.data.room.MetaEntity
import fr.axllvy.timetabler.data.room.RoomEventStore
import fr.axllvy.timetabler.data.room.inMemoryDatabase
import fr.axllvy.timetabler.domain.EventId
import fr.axllvy.timetabler.domain.Revision
import fr.axllvy.timetabler.wire.EventDto
import fr.axllvy.timetabler.wire.EventsPage
import fr.axllvy.timetabler.wire.VenueDto
import fr.axllvy.timetabler.wire.WIRE_JSON
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import java.io.IOException
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class EventSyncTest {

  private val database = inMemoryDatabase()
  private val store = RoomEventStore(database)
  private val sinces = mutableListOf<String?>()

  private fun event(id: String, revision: Long) =
    EventDto(
      EventId(id),
      Revision(revision),
      "Night $id",
      LocalDate(2026, 10, 30),
      Instant.parse("2026-10-30T22:30:00Z"),
      null,
      VenueDto("Rex Club", null),
      emptyList(),
      null,
      emptyList(),
    )

  private fun apiServing(vararg pages: EventsPage): EventsApi {
    var served = 0
    val engine = MockEngine { request ->
      sinces += request.url.parameters["since"]
      val page =
        pages.getOrElse(served++) { EventsPage(emptyList(), emptyList(), pages.last().next, false) }
      respond(
        WIRE_JSON.encodeToString(EventsPage.serializer(), page),
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
      )
    }
    return EventsApi(HttpClient(engine), "https://tt.example")
  }

  @Test
  fun `drains every page of the feed`() = runTest {
    val sync =
      EventSync(
        apiServing(
          EventsPage(listOf(event("a", 1)), emptyList(), Revision(1), hasMore = true),
          EventsPage(listOf(event("b", 2)), emptyList(), Revision(2), hasMore = true),
          EventsPage(listOf(event("c", 3)), emptyList(), Revision(3), hasMore = false),
        ),
        store,
      )

    sync.syncOnce() shouldBe SyncOutcome.Ok

    sinces shouldContainExactly listOf("0", "1", "2")
    store.events().first().map { it.id.value }.sorted() shouldContainExactly listOf("a", "b", "c")
  }

  @Test
  fun `a new server epoch starts the store over`() = runTest {
    store.apply(
      EventsPage(listOf(event("old", 7)), emptyList(), Revision(7), false, epoch = "before")
    )
    val sync =
      EventSync(
        apiServing(
          EventsPage(emptyList(), emptyList(), Revision(2), false, epoch = "after"),
          EventsPage(listOf(event("new", 2)), emptyList(), Revision(2), false, epoch = "after"),
        ),
        store,
      )

    sync.syncOnce() shouldBe SyncOutcome.Ok

    sinces shouldContainExactly listOf("7", "0")
    store.events().first().map { it.id.value } shouldContainExactly listOf("new")
  }

  @Test
  fun `a store behind a purged removal starts over`() = runTest {
    store.apply(EventsPage(listOf(event("old", 7)), emptyList(), Revision(7), false))
    val sync =
      EventSync(
        apiServing(
          EventsPage(emptyList(), emptyList(), Revision(12), false, purgedThrough = Revision(9)),
          EventsPage(
            listOf(event("new", 12)),
            emptyList(),
            Revision(12),
            false,
            purgedThrough = Revision(9),
          ),
        ),
        store,
      )

    sync.syncOnce() shouldBe SyncOutcome.Ok

    sinces shouldContainExactly listOf("7", "0")
    store.events().first().map { it.id.value } shouldContainExactly listOf("new")
  }

  @Test
  fun `a store past every purged removal keeps syncing incrementally`() = runTest {
    store.apply(EventsPage(listOf(event("a", 9)), emptyList(), Revision(9), false))
    val sync =
      EventSync(
        apiServing(
          EventsPage(
            listOf(event("b", 12)),
            emptyList(),
            Revision(12),
            false,
            purgedThrough = Revision(9),
          )
        ),
        store,
      )

    sync.syncOnce() shouldBe SyncOutcome.Ok

    sinces shouldContainExactly listOf("9")
    store.events().first().map { it.id.value }.sorted() shouldContainExactly listOf("a", "b")
  }

  @Test
  fun `a fresh store ignores purged removals`() = runTest {
    val sync =
      EventSync(
        apiServing(
          EventsPage(
            listOf(event("a", 12)),
            emptyList(),
            Revision(12),
            false,
            purgedThrough = Revision(9),
          )
        ),
        store,
      )

    sync.syncOnce() shouldBe SyncOutcome.Ok

    sinces shouldContainExactly listOf("0")
  }

  @Test
  fun `pages within a run are not checked against purged removals`() = runTest {
    val sync =
      EventSync(
        apiServing(
          EventsPage(
            listOf(event("a", 5)),
            emptyList(),
            Revision(5),
            hasMore = true,
            purgedThrough = Revision(9),
          ),
          EventsPage(
            listOf(event("b", 12)),
            emptyList(),
            Revision(12),
            hasMore = false,
            purgedThrough = Revision(9),
          ),
        ),
        store,
      )

    sync.syncOnce() shouldBe SyncOutcome.Ok

    sinces shouldContainExactly listOf("0", "5")
    store.events().first().map { it.id.value }.sorted() shouldContainExactly listOf("a", "b")
  }

  @Test
  fun `a purge during a resumed run starts the store over`() = runTest {
    store.apply(EventsPage(listOf(event("a", 7)), emptyList(), Revision(7), false))
    val sync =
      EventSync(
        apiServing(
          EventsPage(listOf(event("b", 8)), emptyList(), Revision(8), hasMore = true),
          EventsPage(emptyList(), emptyList(), Revision(12), false, purgedThrough = Revision(10)),
          EventsPage(
            listOf(event("c", 12)),
            emptyList(),
            Revision(12),
            false,
            purgedThrough = Revision(10),
          ),
        ),
        store,
      )

    sync.syncOnce() shouldBe SyncOutcome.Ok

    sinces shouldContainExactly listOf("7", "8", "0")
    store.events().first().map { it.id.value } shouldContainExactly listOf("c")
  }

  @Test
  fun `a stored schema older than the app starts the store over`() = runTest {
    store.apply(EventsPage(listOf(event("old", 7)), emptyList(), Revision(7), false))
    database.eventDao().setMeta(MetaEntity(EventDao.SCHEMA_VERSION, "0"))
    val sync =
      EventSync(
        apiServing(EventsPage(listOf(event("new", 2)), emptyList(), Revision(2), false)),
        store,
      )

    sync.syncOnce() shouldBe SyncOutcome.Ok

    sinces shouldContainExactly listOf("0")
    store.events().first().map { it.id.value } shouldContainExactly listOf("new")
  }

  @Test
  fun `a store synced before schema versions starts over`() = runTest {
    database.eventDao().setMeta(MetaEntity(EventDao.LAST_REVISION, "7"))
    database.eventDao().upsertAll(listOf(EventEntity("old", 7, """{"id":"old"}""")))
    val sync =
      EventSync(apiServing(EventsPage(emptyList(), emptyList(), Revision(0), false)), store)

    sync.syncOnce() shouldBe SyncOutcome.Ok

    sinces shouldContainExactly listOf("0")
    store.events().first() shouldBe emptyList()
  }

  @Test
  fun `a stale schema is cleared even when offline`() = runTest {
    store.apply(EventsPage(listOf(event("old", 7)), emptyList(), Revision(7), false))
    database.eventDao().setMeta(MetaEntity(EventDao.SCHEMA_VERSION, "0"))
    val offline =
      EventsApi(HttpClient(MockEngine { throw IOException("no route") }), "https://tt.example")

    EventSync(offline, store).syncOnce().shouldBeInstanceOf<SyncOutcome.Offline>()

    store.hasEverSynced() shouldBe false
  }

  @Test
  fun `a later sync starts from the stored revision`() = runTest {
    store.apply(EventsPage(listOf(event("a", 7)), emptyList(), Revision(7), hasMore = false))

    EventSync(apiServing(EventsPage(emptyList(), emptyList(), Revision(7), false)), store)
      .syncOnce()

    sinces shouldContainExactly listOf("7")
  }

  @Test
  fun `a failure that is not an exception is reported as offline`() = runTest {
    // On wasm a failed browser fetch surfaces as a JsException, which is a Throwable, not an
    // Exception.
    val api =
      EventsApi(
        HttpClient(MockEngine { throw object : Throwable("Fail to fetch") {} }),
        "https://tt.example",
      )
    val sync = EventSync(api, store)

    sync.syncOnce().shouldBeInstanceOf<SyncOutcome.Offline>()

    sync.state.value shouldBe SyncState.OFFLINE
  }

  @Test
  fun `a server that never answers is reported as offline`(): Unit = runBlocking {
    val hanging = MockEngine { kotlinx.coroutines.awaitCancellation() }
    val api =
      EventsApi(
        HttpClient(hanging) { timeTablerDefaults(requestTimeout = 300.milliseconds) },
        "https://tt.example",
      )

    EventSync(api, store).syncOnce().shouldBeInstanceOf<SyncOutcome.Offline>()
  }

  @Test
  fun `no network keeps the stored events`() = runTest {
    store.apply(EventsPage(listOf(event("a", 1)), emptyList(), Revision(1), hasMore = false))
    val offline =
      EventsApi(HttpClient(MockEngine { throw IOException("no route") }), "https://tt.example")
    val sync = EventSync(offline, store)

    val outcome = sync.syncOnce()

    outcome.shouldBeInstanceOf<SyncOutcome.Offline>()
    sync.state.value shouldBe SyncState.OFFLINE
    store.events().first().map { it.id.value } shouldContainExactly listOf("a")
  }
}
