package fr.axllvy.timetabler.server.http

import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue

/** Runs a real headless Chromium, so only when `TT_BROWSER_TESTS` is `true`. */
class PlaywrightBrowserTest {

  init {
    assumeTrue(System.getenv("TT_BROWSER_TESTS") == "true")
  }

  private val imageHits = AtomicInteger()
  @Volatile private var userAgent: String? = null

  private val server =
    embeddedServer(Netty, port = 0) {
        routing {
          get("/page") {
            userAgent = call.request.header(HttpHeaders.UserAgent)
            call.respondText(
              """<html><body><img src="/poster.png"><a href="/fr/events/orka">Orka</a></body></html>""",
              ContentType.Text.Html,
            )
          }
          get("/poster.png") {
            imageHits.incrementAndGet()
            call.respondBytes(ByteArray(0), ContentType.Image.PNG)
          }
          get("/busy") { call.respondText("slow down", status = HttpStatusCode.TooManyRequests) }
          get("/slow") {
            delay(3.seconds)
            call.respondText("late", ContentType.Text.Html)
          }
        }
      }
      .start(wait = false)

  private val baseUrl = runBlocking {
    "http://localhost:${server.engine.resolvedConnectors().first().port}"
  }

  @AfterTest
  fun stop() {
    server.stop(gracePeriodMillis = 0, timeoutMillis = 1000)
  }

  @Test
  fun `loads a page without its images`() =
    runBlocking<Unit> {
      val browser = PlaywrightBrowser("TimeTabler/test")

      val result = browser.session { it.load("$baseUrl/page") }

      result.shouldBeInstanceOf<FetchResult.Ok>().body shouldContain "/fr/events/orka"
      imageHits.get() shouldBe 0
      userAgent!! shouldContain "Chrome/"
      userAgent!! shouldContain "TimeTabler/test"
    }

  @Test
  fun `an HTTP error is a failed load with its status`() =
    runBlocking<Unit> {
      val browser = PlaywrightBrowser("TimeTabler/test")

      val result = browser.session { it.load("$baseUrl/busy") }

      result.shouldBeInstanceOf<FetchResult.Failed>().status shouldBe 429
    }

  @Test
  fun `a timeout is a failed load without status`() =
    runBlocking<Unit> {
      val browser = PlaywrightBrowser("TimeTabler/test", timeout = 1.seconds)

      val result = browser.session { it.load("$baseUrl/slow") }

      val failed = result.shouldBeInstanceOf<FetchResult.Failed>()
      failed.status.shouldBeNull()
      failed.reason shouldStartWith "Timeout"
    }

  @Test
  fun `waits the interval between two loads`() =
    runBlocking<Unit> {
      val browser = PlaywrightBrowser("TimeTabler/test", minInterval = 1.seconds)

      val elapsed = browser.session { loader ->
        loader.load("$baseUrl/page")
        val second = TimeSource.Monotonic.markNow()
        loader.load("$baseUrl/page")
        second.elapsedNow()
      }

      elapsed shouldBeGreaterThanOrEqualTo 1.seconds
    }
}
