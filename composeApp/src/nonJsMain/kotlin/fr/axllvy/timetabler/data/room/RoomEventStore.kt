package fr.axllvy.timetabler.data.room

import fr.axllvy.timetabler.data.EventStoreLocal
import fr.axllvy.timetabler.domain.EventId
import fr.axllvy.timetabler.domain.Revision
import fr.axllvy.timetabler.wire.EventDto
import fr.axllvy.timetabler.wire.EventsPage
import fr.axllvy.timetabler.wire.WIRE_JSON
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [EventStoreLocal] on Room, for Android, iOS and the JVM. */
class RoomEventStore(database: TimeTablerDatabase) : EventStoreLocal {

  private val dao = database.eventDao()

  override fun events(): Flow<List<EventDto>> = dao.observeAll().map { rows -> rows.map { it.toDto() } }

  override fun event(id: EventId): Flow<EventDto?> = dao.observe(id.value).map { it?.toDto() }

  override suspend fun apply(page: EventsPage) {
    dao.applyPage(
      events = page.events.map { EventEntity(it.id.value, it.revision.value, WIRE_JSON.encodeToString(EventDto.serializer(), it)) },
      removed = page.removed.map { it.value },
      revision = page.next.value,
    )
  }

  override suspend fun lastRevision(): Revision =
    dao.meta(EventDao.LAST_REVISION)?.toLong()?.let(::Revision) ?: Revision.ZERO

  override suspend fun hasEverSynced(): Boolean = dao.meta(EventDao.LAST_REVISION) != null

  private fun EventEntity.toDto(): EventDto = WIRE_JSON.decodeFromString(EventDto.serializer(), json)
}
