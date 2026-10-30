@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.axllvy.timetabler.data

import com.juul.indexeddb.openDatabase
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.random.Random

private fun rawRecord(key: String, value: String): JsAny = js("({ key: key, value: value })")

class IndexedDbEventStoreTest : EventStoreLocalContract() {
  private val databaseName = "timetabler-test-${Random.nextLong()}"

  override suspend fun newStore(): EventStoreLocal = IndexedDbEventStore(databaseName)

  override suspend fun writeRawRow(id: String, json: String) {
    val database = openDatabase(databaseName, 1) { _, _, _ -> }
    database.writeTransaction("events") { objectStore("events").put(rawRecord(id, json)) }
    database.close()
  }
}
