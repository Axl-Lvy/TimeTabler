package fr.axllvy.timetabler.data

import fr.axllvy.timetabler.data.room.RoomEventStore
import fr.axllvy.timetabler.data.room.inMemoryDatabase

class RoomEventStoreTest : EventStoreLocalContract() {
  override suspend fun newStore(): EventStoreLocal = RoomEventStore(inMemoryDatabase())
}
