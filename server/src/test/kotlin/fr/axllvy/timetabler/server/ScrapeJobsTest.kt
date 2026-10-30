package fr.axllvy.timetabler.server

import fr.axllvy.timetabler.domain.Revision
import fr.axllvy.timetabler.server.dice.DiceScraper
import fr.axllvy.timetabler.server.events.EventStore
import fr.axllvy.timetabler.server.events.ScrapedListing
import fr.axllvy.timetabler.server.health.ScrapeRuns
import fr.axllvy.timetabler.server.http.PoliteFetcher
import fr.axllvy.timetabler.server.posters.PosterCache
import fr.axllvy.timetabler.wire.ListingStatus
import fr.axllvy.timetabler.wire.Source
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class ScrapeJobsTest {

  private val clock =
    object : Clock {
      override fun now() = Instant.parse("2026-11-07T09:00:00Z")
    }
  private val db = PostgresTestDb.freshDataSource()
  private val store = EventStore(db, clock)
  private val runs = ScrapeRuns(store)

  @AfterTest
  fun close() {
    db.close()
  }

  private val posterDir = createTempDirectory()
  private val png =
    ByteArrayOutputStream()
      .also { ImageIO.write(BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", it) }
      .toByteArray()

  private fun listing(id: String, startsAt: Instant, posterUrl: String? = null) =
    ScrapedListing(
      Source.DICE,
      id,
      "https://dice.fm/event/$id",
      "Night $id",
      startsAt,
      null,
      "Rex Club",
      null,
      ListingStatus.ON_SALE,
      posterUrl,
      emptyList(),
    )

  private fun TestScope.jobs(engine: MockEngine): ScrapeJobs {
    val fetcher =
      PoliteFetcher(HttpClient(engine), "TimeTabler/test", timeSource = testScheduler.timeSource)
    return ScrapeJobs(
      DiceScraper(fetcher, store, clock),
      store,
      PosterCache(posterDir, fetcher),
      runs,
      30.days,
      clock,
    )
  }

  private fun TestScope.throttledJobs() =
    jobs(MockEngine { respondError(HttpStatusCode.TooManyRequests) })

  @Test
  fun `refresh prefetches the posters of events starting soon`() = runTest {
    store.upsert(
      listing("soon", Instant.parse("2026-11-07T22:00:00Z"), "https://img.example/soon.png")
    )
    store.upsert(
      listing("later", Instant.parse("2026-11-12T22:00:00Z"), "https://img.example/later.png")
    )
    val posters = mutableListOf<String>()
    val jobs =
      jobs(
        MockEngine { request ->
          val url = request.url.toString()
          if ("img.example" in url) {
            posters += url
            respond(png)
          } else respondError(HttpStatusCode.NotFound)
        }
      )

    jobs.refresh()

    posters shouldContainExactly listOf("https://img.example/soon.png")
    posterDir.listDirectoryEntries("*.jpg").size shouldBe 1
    store.scrapeRuns().getValue("refresh").posters shouldBe 1
  }

  @Test
  fun `a failed agenda scrape still archives past nights`() = runTest {
    val old = store.upsert(listing("old", Instant.parse("2026-11-04T22:00:00Z"))).id
    store.upsert(listing("next", Instant.parse("2026-11-08T22:00:00Z")))
    val jobs = throttledJobs()

    jobs.agenda()

    store.page(Revision.ZERO, 100).removed shouldContainExactly listOf(old)
  }

  @Test
  fun `a failed agenda is recorded with no success`() = runTest {
    throttledJobs().agenda()

    val run = store.scrapeRuns().getValue("agenda")
    run.lastAttemptAt shouldBe clock.now()
    run.lastSuccessAt.shouldBeNull()
    run.consecutiveFailures shouldBe 1
    run.failure!! shouldContain "429"
  }

  @Test
  fun `a job whose database is gone is recorded and runs again`() = runTest {
    val jobs = throttledJobs()
    db.close()

    jobs.agenda()
    jobs.agenda()

    val run = runs.current()!!.getValue("agenda")
    run.failure!! shouldContain "threw"
    run.consecutiveFailures shouldBe 2
  }
}
