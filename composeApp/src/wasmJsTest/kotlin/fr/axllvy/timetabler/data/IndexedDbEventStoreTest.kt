package fr.axllvy.timetabler.data

import kotlin.random.Random

class IndexedDbEventStoreTest : EventStoreLocalContract() {
  override suspend fun newStore(): EventStoreLocal =
    IndexedDbEventStore(databaseName = "timetabler-test-${Random.nextLong()}")
}
