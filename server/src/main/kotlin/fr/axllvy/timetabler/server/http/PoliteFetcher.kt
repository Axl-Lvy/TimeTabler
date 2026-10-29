package fr.axllvy.timetabler.server.http

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.takeFrom
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

private const val MAX_REDIRECTS = 5

/** HTTP GET client that waits [minInterval] between two requests to the same host. */
class PoliteFetcher(
  client: HttpClient,
  private val userAgent: String,
  private val minInterval: Duration = 2.seconds,
  private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {

  private class HostSlot {
    val mutex = Mutex()
    var last: ComparableTimeMark? = null
  }

  private val hosts = ConcurrentHashMap<String, HostSlot>()

  // Redirects are followed here, not by Ktor, so each hop waits its turn like any other request.
  private val client = client.config { followRedirects = false }

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
    var target = Url(url)
    repeat(MAX_REDIRECTS + 1) {
      val response =
        try {
          throttled(target.host) { client.get(target) { header(HttpHeaders.UserAgent, userAgent) } }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          return onFailure(null, e.message ?: e::class.simpleName.orEmpty())
        }
      val location = response.headers[HttpHeaders.Location]
      when {
        response.status.value in 300..399 && location != null -> target = URLBuilder(target).takeFrom(location).build()
        response.status.isSuccess() -> return read(response)
        else -> return onFailure(response.status.value, response.status.description)
      }
    }
    return onFailure(null, "too many redirects")
  }

  private suspend fun <T> throttled(host: String, block: suspend () -> T): T {
    val slot = hosts.computeIfAbsent(host) { HostSlot() }
    return slot.mutex.withLock {
      slot.last?.let { delay(minInterval - it.elapsedNow()) }
      try {
        block()
      } finally {
        slot.last = timeSource.markNow()
      }
    }
  }
}
