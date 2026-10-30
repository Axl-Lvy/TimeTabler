package fr.axllvy.timetabler.data

import fr.axllvy.timetabler.domain.EventId
import fr.axllvy.timetabler.domain.Revision
import fr.axllvy.timetabler.domain.nightDateOf
import fr.axllvy.timetabler.wire.EventDto
import fr.axllvy.timetabler.wire.VenueDto
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest

class PosterPrefetcherTest {

  private val now = Instant.parse("2026-10-30T18:00:00Z")
  private val clock =
    object : Clock {
      override fun now() = now
    }

  private class FakePosterCache(
    cached: Set<String> = emptySet(),
    private val failing: Set<String> = emptySet(),
  ) : PosterCache {
    val cached = cached.toMutableSet()
    val downloads = mutableListOf<String>()
    var gate = CompletableDeferred(Unit)

    override suspend fun contains(url: String) = url in cached

    override suspend fun download(url: String) {
      gate.await()
      downloads += url
      if (url in failing) throw IllegalStateException("Poster answered 404")
      cached += url
    }
  }

  private fun event(id: String, startsIn: Duration, posterPath: String? = "/v1/posters/$id?v=1") =
    EventDto(
      id = EventId(id),
      revision = Revision(1),
      title = "Night $id",
      nightDate = nightDateOf(now + startsIn),
      startsAt = now + startsIn,
      endsAt = null,
      venue = VenueDto("Rex Club", null),
      lineup = emptyList(),
      posterPath = posterPath,
      listings = emptyList(),
    )

  @Test
  fun `posters of events starting in the next 48 hours are downloaded soonest first`() = runTest {
    val cache = FakePosterCache()
    val events =
      listOf(
        event("later", startsIn = 47.hours),
        event("soon", startsIn = 2.hours),
        event("now", startsIn = Duration.ZERO),
      )

    val downloaded = PosterPrefetcher(cache, "https://tt", clock).prefetch(events)

    downloaded shouldBe 3
    cache.downloads shouldContainExactly
      listOf(
        "https://tt/v1/posters/now?v=1",
        "https://tt/v1/posters/soon?v=1",
        "https://tt/v1/posters/later?v=1",
      )
  }

  @Test
  fun `events started already or starting after 48 hours are skipped`() = runTest {
    val cache = FakePosterCache()
    val events =
      listOf(
        event("started", startsIn = -1.minutes),
        event("far", startsIn = 48.hours + 1.minutes),
        event("bare", startsIn = 1.hours, posterPath = null),
      )

    PosterPrefetcher(cache, "https://tt", clock).prefetch(events)

    cache.downloads.shouldBeEmpty()
  }

  @Test
  fun `posters already cached at their current hash are skipped`() = runTest {
    val cache = FakePosterCache(cached = setOf("https://tt/v1/posters/a?v=1"))

    val downloaded =
      PosterPrefetcher(cache, "https://tt", clock)
        .prefetch(listOf(event("a", startsIn = 1.hours), event("b", startsIn = 2.hours)))

    downloaded shouldBe 1
    cache.downloads shouldContainExactly listOf("https://tt/v1/posters/b?v=1")
  }

  @Test
  fun `a poster cached at an older hash is downloaded again`() = runTest {
    val cache = FakePosterCache(cached = setOf("https://tt/v1/posters/a?v=old"))

    PosterPrefetcher(cache, "https://tt", clock)
      .prefetch(listOf(event("a", startsIn = 1.hours, posterPath = "/v1/posters/a?v=new")))

    cache.downloads shouldContainExactly listOf("https://tt/v1/posters/a?v=new")
  }

  @Test
  fun `a failing poster does not stop the others`() = runTest {
    val cache = FakePosterCache(failing = setOf("https://tt/v1/posters/a?v=1"))

    val downloaded =
      PosterPrefetcher(cache, "https://tt", clock)
        .prefetch(listOf(event("a", startsIn = 1.hours), event("b", startsIn = 2.hours)))

    downloaded shouldBe 1
    cache.cached shouldBe mutableSetOf("https://tt/v1/posters/b?v=1")
  }

  @Test
  fun `a run started while another is in progress does nothing`() = runTest {
    val cache = FakePosterCache().apply { gate = CompletableDeferred() }
    val prefetcher = PosterPrefetcher(cache, "https://tt", clock)
    val events = listOf(event("a", startsIn = 1.hours))
    val first = async { prefetcher.prefetch(events) }
    testScheduler.runCurrent()

    val second = prefetcher.prefetch(events)
    cache.gate.complete(Unit)

    second shouldBe 0
    first.await() shouldBe 1
    cache.downloads shouldContainExactly listOf("https://tt/v1/posters/a?v=1")
  }
}
