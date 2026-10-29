package fr.axllvy.timetabler.server.http

import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlin.test.Test
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class PoliteFetcherTest {

  private class Recorded(val host: String, val atMillis: Long, val userAgent: String?)

  private fun TestScope.fetcher(
    recorded: MutableList<Recorded>,
    status: HttpStatusCode = HttpStatusCode.OK,
  ): PoliteFetcher {
    val engine = MockEngine { request ->
      recorded +=
        Recorded(
          request.url.host,
          testScheduler.currentTime,
          request.headers[HttpHeaders.UserAgent],
        )
      if (status == HttpStatusCode.OK) respond("body of ${request.url.host}")
      else respondError(status)
    }
    return PoliteFetcher(
      HttpClient(engine),
      "TimeTabler/test",
      timeSource = testScheduler.timeSource,
    )
  }

  @Test
  fun `spaces requests to one host by two seconds`() = runTest {
    val recorded = mutableListOf<Recorded>()
    val fetcher = fetcher(recorded)

    fetcher.get("https://dice.fm/a")
    fetcher.get("https://dice.fm/b")

    recorded[1].atMillis - recorded[0].atMillis shouldBeGreaterThanOrEqual 2_000
  }

  @Test
  fun `does not space requests to different hosts`() = runTest {
    val recorded = mutableListOf<Recorded>()
    val fetcher = fetcher(recorded)

    fetcher.get("https://dice.fm/a")
    fetcher.get("https://shotgun.live/a")

    recorded[1].atMillis shouldBe recorded[0].atMillis
  }

  @Test
  fun `sends the user agent and returns the body`() = runTest {
    val recorded = mutableListOf<Recorded>()

    val result = fetcher(recorded).get("https://dice.fm/a")

    result shouldBe FetchResult.Ok("body of dice.fm")
    recorded.single().userAgent shouldBe "TimeTabler/test"
  }

  @Test
  fun `follows a redirect as a separately spaced request`() = runTest {
    val times = mutableListOf<Long>()
    val engine = MockEngine { request ->
      times += testScheduler.currentTime
      if (request.url.encodedPath == "/event/1")
        respond(
          "",
          HttpStatusCode.PermanentRedirect,
          headersOf(HttpHeaders.Location, "/event/slug-1"),
        )
      else respond("page ${request.url.encodedPath}")
    }
    val fetcher =
      PoliteFetcher(HttpClient(engine), "TimeTabler/test", timeSource = testScheduler.timeSource)

    val result = fetcher.get("https://dice.fm/event/1")

    result shouldBe FetchResult.Ok("page /event/slug-1")
    times[1] - times[0] shouldBeGreaterThanOrEqual 2_000
  }

  @Test
  fun `reports a throttled response as failed`() = runTest {
    val result = fetcher(mutableListOf(), HttpStatusCode.TooManyRequests).get("https://dice.fm/a")

    result.shouldBeInstanceOf<FetchResult.Failed>().status shouldBe 429
  }

  @Test
  fun `reports a network error as failed without status`() = runTest {
    val engine = MockEngine { throw IOException("connection reset") }
    val fetcher =
      PoliteFetcher(HttpClient(engine), "TimeTabler/test", timeSource = testScheduler.timeSource)

    val result = fetcher.get("https://dice.fm/a")

    result.shouldBeInstanceOf<FetchResult.Failed>().status shouldBe null
  }
}
