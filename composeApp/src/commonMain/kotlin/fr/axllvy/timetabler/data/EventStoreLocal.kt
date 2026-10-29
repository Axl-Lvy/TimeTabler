package fr.axllvy.timetabler.data

import fr.axllvy.timetabler.domain.EventId
import fr.axllvy.timetabler.domain.Revision
import fr.axllvy.timetabler.wire.EventDto
import fr.axllvy.timetabler.wire.EventsPage
import kotlinx.coroutines.flow.Flow

/** The client's offline copy of the event feed. */
interface EventStoreLocal {

  /** Every stored event, re-emitted after each change. */
  fun events(): Flow<List<EventDto>>

  /** One stored event, or null when it is not stored. */
  fun event(id: EventId): Flow<EventDto?>

  /** Stores [page]'s events, deletes its removed ids and records its revision, all at once. */
  suspend fun apply(page: EventsPage)

  /** Revision of the last applied page, [Revision.ZERO] before the first. */
  suspend fun lastRevision(): Revision

  /** Whether a page has ever been applied. */
  suspend fun hasEverSynced(): Boolean

  /** Epoch of the server database the stored events came from, null before the first page. */
  suspend fun epoch(): String?

  /** Deletes every stored event and setting, as if the app was just installed. */
  suspend fun clear()
}
