@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.axllvy.timetabler.data

import com.juul.indexeddb.Database
import com.juul.indexeddb.Key
import com.juul.indexeddb.KeyPath
import com.juul.indexeddb.openDatabase
import fr.axllvy.timetabler.domain.EventId
import fr.axllvy.timetabler.domain.Revision
import fr.axllvy.timetabler.wire.EventDto
import fr.axllvy.timetabler.wire.EventsPage
import fr.axllvy.timetabler.wire.WIRE_JSON
import fr.axllvy.timetabler.wire.WIRE_SCHEMA_VERSION
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.toJsString
import kotlin.js.toList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val EVENTS = "events"
private const val META = "meta"
private const val LAST_REVISION = "lastRevision"
private const val EPOCH = "epoch"
private const val WIRE_SCHEMA = "schemaVersion"
// Bump to recreate every object store on the next open; there are no migrations.
private const val DATABASE_VERSION = 1

private external interface JsRecord : JsAny {
  var key: String
  var value: String
}

private fun emptyRecord(): JsRecord = js("({})")

private fun record(key: String, value: String): JsRecord {
  val record = emptyRecord()
  record.key = key
  record.value = value
  return record
}

/** [EventStoreLocal] on the browser's IndexedDB, for the web client. */
class IndexedDbEventStore(private val databaseName: String = "timetabler") : EventStoreLocal {

  private val openLock = Mutex()
  private var database: Database? = null
  private val cache = MutableStateFlow<List<EventDto>?>(null)

  private suspend fun db(): Database = openLock.withLock {
    database
      ?: openDatabase(databaseName, DATABASE_VERSION) { db, _, _ ->
          listOf(EVENTS, META).forEach { runCatching { db.deleteObjectStore(it) } }
          db.createObjectStore(EVENTS, KeyPath("key"))
          db.createObjectStore(META, KeyPath("key"))
        }
        .also { database = it }
  }

  private suspend fun reload() {
    val rows = db().transaction(EVENTS) { objectStore(EVENTS).getAll() }.toList()
    // A row written in an older schema may not decode; the next sync replaces it.
    cache.value = rows.mapNotNull { decode(it!!.unsafeCast<JsRecord>().value) }
  }

  // IllegalArgumentException also covers SerializationException and malformed values.
  private fun decode(json: String): EventDto? =
    try {
      WIRE_JSON.decodeFromString(EventDto.serializer(), json)
    } catch (_: IllegalArgumentException) {
      null
    }

  override fun events(): Flow<List<EventDto>> =
    cache.onStart { if (cache.value == null) reload() }.filterNotNull()

  override fun event(id: EventId): Flow<EventDto?> =
    events().map { all -> all.firstOrNull { it.id == id } }

  override suspend fun apply(page: EventsPage) {
    db().writeTransaction(EVENTS, META) {
      val events = objectStore(EVENTS)
      page.events.forEach {
        events.put(record(it.id.value, WIRE_JSON.encodeToString(EventDto.serializer(), it)))
      }
      page.removed.forEach { events.delete(Key(it.value.toJsString())) }
      objectStore(META).put(record(LAST_REVISION, page.next.value.toString()))
      objectStore(META).put(record(EPOCH, page.epoch))
      objectStore(META).put(record(WIRE_SCHEMA, WIRE_SCHEMA_VERSION.toString()))
    }
    reload()
  }

  override suspend fun lastRevision(): Revision =
    meta(LAST_REVISION)?.toLong()?.let(::Revision) ?: Revision.ZERO

  override suspend fun hasEverSynced(): Boolean = meta(LAST_REVISION) != null

  override suspend fun epoch(): String? = meta(EPOCH)

  override suspend fun schemaVersion(): Int? = meta(WIRE_SCHEMA)?.toIntOrNull()

  override suspend fun clear() {
    db().writeTransaction(EVENTS, META) {
      objectStore(EVENTS).clear()
      objectStore(META).clear()
    }
    reload()
  }

  private suspend fun meta(key: String): String? =
    db()
      .transaction(META) { objectStore(META).get(Key(key.toJsString())) }
      ?.unsafeCast<JsRecord>()
      ?.value
}
