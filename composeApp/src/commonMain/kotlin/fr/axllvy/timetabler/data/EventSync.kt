package fr.axllvy.timetabler.data

import co.touchlab.kermit.Logger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Result of one sync attempt. */
sealed interface SyncOutcome {
  /** The local store caught up with the server. */
  data object Ok : SyncOutcome

  /** The server could not be reached; the local store is unchanged since the last good page. */
  data class Offline(val cause: String) : SyncOutcome
}

/** What the sync is doing right now. */
enum class SyncState {
  IDLE,
  SYNCING,
  OFFLINE,
}

/** Pulls the server's event feed into the local store. */
class EventSync(private val api: EventsApi, private val store: EventStoreLocal) {

  private val log = Logger.withTag("EventSync")
  private val running = Mutex()
  private val _state = MutableStateFlow(SyncState.IDLE)

  /** Current sync state, for showing progress or an offline hint. */
  val state: StateFlow<SyncState> = _state.asStateFlow()

  /**
   * Applies every feed page after the stored revision; concurrent calls run one after another. A
   * page from a new server epoch clears the store and starts again from the first revision.
   */
  suspend fun syncOnce(): SyncOutcome = running.withLock {
    _state.value = SyncState.SYNCING
    try {
      while (true) {
        val page = api.page(store.lastRevision())
        val epoch = store.epoch()
        if (epoch != null && epoch != page.epoch) {
          // The server database was restored or reseeded: its revisions no longer match ours.
          log.i { "Server epoch changed, syncing from scratch" }
          store.clear()
          continue
        }
        store.apply(page)
        if (!page.hasMore) break
      }
      _state.value = SyncState.IDLE
      SyncOutcome.Ok
    } catch (e: CancellationException) {
      _state.value = SyncState.IDLE
      throw e
    } catch (e: Throwable) {
      // Throwable, not Exception: a failed browser fetch on wasm is a JsException.
      log.w { "Sync failed: ${e.message}" }
      _state.value = SyncState.OFFLINE
      SyncOutcome.Offline(e.message ?: e::class.simpleName.orEmpty())
    }
  }
}
