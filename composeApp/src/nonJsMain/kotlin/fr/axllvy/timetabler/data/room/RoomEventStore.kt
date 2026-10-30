package fr.axllvy.timetabler.data.room

import fr.axllvy.timetabler.data.EventStoreLocal
import fr.axllvy.timetabler.domain.EventId
import fr.axllvy.timetabler.domain.Revision
import fr.axllvy.timetabler.wire.EventDto
import fr.axllvy.timetabler.wire.EventsPage
import fr.axllvy.timetabler.wire.WIRE_JSON
import fr.axllvy.timetabler.wire.WIRE_SCHEMA_VERSION
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException

/** [EventStoreLocal] on Room, for Android, iOS and the JVM. */
class RoomEventStore(database: TimeTablerDatabase) : EventStoreLocal {

  private val dao = database.eventDao()

  override fun events(): Flow<List<EventDto>> =
    dao.observeAll().map { rows -> rows.mapNotNull { it.toDto() } }

  override fun event(id: EventId): Flow<EventDto?> = dao.observe(id.value).map { it?.toDto() }

  override suspend fun apply(page: EventsPage) {
    dao.applyPage(
      events =
        page.events.map {
          EventEntity(
            it.id.value,
            it.revision.value,
            WIRE_JSON.encodeToString(EventDto.serializer(), it),
          )
        },
      removed = page.removed.map { it.value },
      revision = page.next.value,
      epoch = page.epoch,
      schemaVersion = WIRE_SCHEMA_VERSION,
    )
  }

  override suspend fun lastRevision(): Revision =
    dao.meta(EventDao.LAST_REVISION)?.toLong()?.let(::Revision) ?: Revision.ZERO

  override suspend fun hasEverSynced(): Boolean = dao.meta(EventDao.LAST_REVISION) != null

  override suspend fun epoch(): String? = dao.meta(EventDao.EPOCH)

  override suspend fun schemaVersion(): Int? = dao.meta(EventDao.SCHEMA_VERSION)?.toIntOrNull()

  override suspend fun clear() = dao.clearAll()

  // A row written in an older schema may not decode; the next sync replaces it.
  private fun EventEntity.toDto(): EventDto? =
    try {
      WIRE_JSON.decodeFromString(EventDto.serializer(), json)
    } catch (_: SerializationException) {
      null
    }
}
