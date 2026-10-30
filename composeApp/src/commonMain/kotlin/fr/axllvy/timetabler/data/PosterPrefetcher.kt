package fr.axllvy.timetabler.data

import co.touchlab.kermit.Logger
import coil3.PlatformContext
import fr.axllvy.timetabler.wire.EventDto
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.sync.Mutex

/** How far ahead of now the posters of starting events are prefetched. */
val POSTER_PREFETCH_WINDOW: Duration = 48.hours

/** Where this client keeps the posters it shows, keyed by their full URL. */
interface PosterCache {

  /** Whether the poster at [url] is cached. */
  suspend fun contains(url: String): Boolean

  /** Downloads the poster at [url] into the cache; throws when it cannot. */
  suspend fun download(url: String)
}

/** The poster cache the image loader of [context] reads from. */
expect fun posterCache(context: PlatformContext): PosterCache

/** Caches the posters of events starting soon, so they show offline even if never displayed. */
class PosterPrefetcher(
  private val cache: PosterCache,
  private val baseUrl: String,
  private val clock: Clock = Clock.System,
) {

  private val log = Logger.withTag("PosterPrefetcher")
  private val running = Mutex()

  /**
   * Downloads the uncached posters of [events] starting within [POSTER_PREFETCH_WINDOW], one at a
   * time, and returns how many were downloaded. Failed posters are skipped, and a call made while
   * another runs does nothing.
   */
  suspend fun prefetch(events: List<EventDto>): Int {
    if (!running.tryLock()) return 0
    try {
      var downloaded = 0
      var failed = 0
      for (url in posterUrls(events)) {
        try {
          if (!cache.contains(url)) {
            cache.download(url)
            downloaded++
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Throwable) {
          // Throwable, not Exception: a failed browser fetch on wasm is a JsException.
          log.d { "Prefetching $url failed: ${e.message}" }
          failed++
        }
      }
      if (failed > 0) log.i { "Prefetched $downloaded posters, $failed failed" }
      return downloaded
    } finally {
      running.unlock()
    }
  }

  private fun posterUrls(events: List<EventDto>): List<String> {
    val now = clock.now()
    val window = now..now + POSTER_PREFETCH_WINDOW
    return events
      .filter { it.posterPath != null && it.startsAt in window }
      .sortedBy { it.startsAt }
      .map { baseUrl + it.posterPath }
      .distinct()
  }
}
