package fr.axllvy.timetabler.data

import fr.axllvy.timetabler.data.room.EventEntity
import fr.axllvy.timetabler.data.room.RoomEventStore
import fr.axllvy.timetabler.data.room.inMemoryDatabase
import fr.axllvy.timetabler.domain.Revision
import fr.axllvy.timetabler.wire.EventsPage
import io.kotest.matchers.collections.shouldContainExactly
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class RoomEventStoreTest : EventStoreLocalContract() {
  override suspend fun newStore(): EventStoreLocal = RoomEventStore(inMemoryDatabase())

  @Test
  fun `rows that no longer decode are skipped`() = runTest {
    val database = inMemoryDatabase()
    val store = RoomEventStore(database)
    store.apply(EventsPage(listOf(event("a", 1)), emptyList(), Revision(1), false))
    database.eventDao().upsertAll(listOf(EventEntity("broken", 2, """{"id":"broken"}""")))

    val stored = store.events().first()

    stored.map { it.id.value } shouldContainExactly listOf("a")
  }
}
