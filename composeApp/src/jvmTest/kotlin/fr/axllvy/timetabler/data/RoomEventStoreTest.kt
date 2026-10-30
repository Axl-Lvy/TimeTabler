package fr.axllvy.timetabler.data

import fr.axllvy.timetabler.data.room.EventEntity
import fr.axllvy.timetabler.data.room.RoomEventStore
import fr.axllvy.timetabler.data.room.inMemoryDatabase

class RoomEventStoreTest : EventStoreLocalContract() {
  private val database = inMemoryDatabase()

  override suspend fun newStore(): EventStoreLocal = RoomEventStore(database)

  override suspend fun writeRawRow(id: String, json: String) {
    database.eventDao().upsertAll(listOf(EventEntity(id, 0, json)))
  }
}
