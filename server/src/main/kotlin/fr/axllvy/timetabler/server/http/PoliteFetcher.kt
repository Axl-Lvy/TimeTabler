package fr.axllvy.timetabler.server.http

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.isSuccess
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Outcome of one fetch: the body, or why there is none. */
sealed interface FetchResult {
  /** The page body of a successful response. */
  data class Ok(val body: String) : FetchResult

  /** A failed fetch; [status] is null when no HTTP response arrived. */
  data class Failed(val status: Int?, val reason: String) : FetchResult
}

/** HTTP GET client that waits [minInterval] between two requests to the same host. */
class PoliteFetcher(
  private val client: HttpClient,
  private val userAgent: String,
  private val minInterval: Duration = 2.seconds,
  private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {

  private class HostSlot {
    val mutex = Mutex()
    var last: ComparableTimeMark? = null
  }

  private val hosts = ConcurrentHashMap<String, HostSlot>()

  /** Fetches [url] as text, suspending until the host's interval has passed. */
  suspend fun get(url: String): FetchResult =
    fetch(url, onFailure = { status, reason -> FetchResult.Failed(status, reason) }) {
      FetchResult.Ok(it.bodyAsText())
    }

  /** Fetches [url] as bytes, or null when the request fails. */
  suspend fun getBytes(url: String): ByteArray? =
    fetch(url, onFailure = { _, _ -> null }) { it.readRawBytes() }

  private suspend fun <T> fetch(
    url: String,
    onFailure: (status: Int?, reason: String) -> T,
    read: suspend (HttpResponse) -> T,
  ): T {
    val slot = hosts.computeIfAbsent(Url(url).host) { HostSlot() }
    return slot.mutex.withLock {
      slot.last?.let { delay(minInterval - it.elapsedNow()) }
      try {
        val response = client.get(url) { header(HttpHeaders.UserAgent, userAgent) }
        if (response.status.isSuccess()) read(response)
        else onFailure(response.status.value, response.status.description)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        onFailure(null, e.message ?: e::class.simpleName.orEmpty())
      } finally {
        slot.last = timeSource.markNow()
      }
    }
  }
}
