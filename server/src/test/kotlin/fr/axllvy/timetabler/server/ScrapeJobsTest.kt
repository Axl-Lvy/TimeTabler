package fr.axllvy.timetabler.server

import fr.axllvy.timetabler.domain.Revision
import fr.axllvy.timetabler.server.dice.DiceScraper
import fr.axllvy.timetabler.server.events.EventStore
import fr.axllvy.timetabler.server.events.ScrapedListing
import fr.axllvy.timetabler.server.http.PoliteFetcher
import fr.axllvy.timetabler.wire.ListingStatus
import fr.axllvy.timetabler.wire.Source
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Clock
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

  @AfterTest
  fun close() {
    db.close()
  }

  private fun listing(id: String, startsAt: Instant) =
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
      null,
      emptyList(),
    )

  private fun TestScope.throttledJobs(): ScrapeJobs {
    val fetcher =
      PoliteFetcher(
        HttpClient(MockEngine { respondError(HttpStatusCode.TooManyRequests) }),
        "TimeTabler/test",
        timeSource = testScheduler.timeSource,
      )
    return ScrapeJobs(DiceScraper(fetcher, store, clock), store, clock)
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
  fun `a job that throws leaves the next run possible`() = runTest {
    val jobs = throttledJobs()
    db.close()

    jobs.agenda()
    jobs.refresh()
  }
}
