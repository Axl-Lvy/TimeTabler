package fr.axllvy.timetabler.server.events

import fr.axllvy.timetabler.domain.Revision
import fr.axllvy.timetabler.server.PostgresTestDb
import fr.axllvy.timetabler.wire.ListingStatus
import fr.axllvy.timetabler.wire.Source
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class EventStoreTest {

  private val now = Instant.parse("2026-10-30T12:00:00Z")
  private val clock =
    object : Clock {
      override fun now() = now
    }
  private val store = EventStore(PostgresTestDb.freshDataSource(), clock)

  private fun listing(
    id: String,
    startsAt: Instant = Instant.parse("2026-10-30T22:30:00Z"),
    venue: String = "La Machine du Moulin Rouge",
    title: String = "Night $id",
  ) =
    ScrapedListing(
      source = Source.DICE,
      externalId = id,
      url = "https://dice.fm/event/$id",
      title = title,
      startsAt = startsAt,
      endsAt = startsAt + 7.hours,
      venueName = venue,
      venueAddress = "90 Boulevard de Clichy",
      status = ListingStatus.ON_SALE,
      posterUrl = "https://img/$id.jpg",
      lineup = emptyList(),
    )

  private fun drain(): List<Pair<String, Revision>> = buildList {
    var since = Revision.ZERO
    do {
      val page = store.page(since, 100)
      page.events.forEach { add(it.title to it.revision) }
      since = page.next
    } while (page.hasMore)
  }

  @Test
  fun `a new listing becomes an event in the feed`() {
    val id = store.upsert(listing("a")).id
    store.setLineup(Source.DICE, "a", listOf("I Hate Models", "Narciss"))

    val event = store.page(Revision.ZERO, 100).events.single()

    event.id shouldBe id
    event.title shouldBe "Night a"
    event.nightDate shouldBe LocalDate(2026, 10, 30)
    event.venue.name shouldBe "La Machine du Moulin Rouge"
    event.lineup shouldBe listOf("I Hate Models", "Narciss")
    event.posterPath shouldBe "/v1/posters/${id.value}"
    event.listings.single().url shouldBe "https://dice.fm/event/a"
  }

  @Test
  fun `an identical listing does not change the revision`() {
    store.upsert(listing("a"))
    val before = store.page(Revision.ZERO, 100).events.single().revision

    val second = store.upsert(listing("a"))

    second.changed shouldBe false
    store.page(Revision.ZERO, 100).events.single().revision shouldBe before
  }

  @Test
  fun `a changed lineup bumps the revision past every other`() {
    store.upsert(listing("a"))
    store.upsert(listing("b"))
    val highest = store.page(Revision.ZERO, 100).next

    store.setLineup(Source.DICE, "a", listOf("Kmyle"))

    val event = store.page(highest, 100).events.single()
    event.title shouldBe "Night a"
    event.revision shouldBeGreaterThan highest
  }

  @Test
  fun `setting the same lineup again changes nothing`() {
    store.upsert(listing("a"))
    store.setLineup(Source.DICE, "a", listOf("Kmyle"))
    val highest = store.page(Revision.ZERO, 100).next

    store.setLineup(Source.DICE, "a", listOf("Kmyle"))

    store.page(highest, 100).events.shouldBeEmpty()
  }

  @Test
  fun `a changed title reports a change`() {
    store.upsert(listing("a"))

    store.upsert(listing("a", title = "Renamed")).changed shouldBe true
  }

  @Test
  fun `venue names differing in case share one venue`() {
    store.upsert(listing("a", venue = "La Machine du Moulin Rouge"))
    store.upsert(listing("b", venue = "la machine du moulin rouge"))

    store.page(Revision.ZERO, 100).events.map { it.venue.name }.toSet() shouldBe
      setOf("La Machine du Moulin Rouge")
  }

  @Test
  fun `paging while an event changes misses nothing`() {
    (1..5).forEach { store.upsert(listing("e$it")) }

    val first = store.page(Revision.ZERO, 2)
    store.upsert(listing("e1", title = "Night e1 updated"))
    val seen = first.events.map { it.title }.toMutableList()
    var page = store.page(first.next, 2)
    seen += page.events.map { it.title }
    while (page.hasMore) {
      page = store.page(page.next, 2)
      seen += page.events.map { it.title }
    }

    seen shouldContainExactly
      listOf("Night e1", "Night e2", "Night e3", "Night e4", "Night e5", "Night e1 updated")
    page.hasMore shouldBe false
  }

  @Test
  fun `archiving moves old events to removed`() {
    val old = store.upsert(listing("old", startsAt = Instant.parse("2026-10-27T22:00:00Z"))).id
    store.upsert(listing("new"))
    val highest = store.page(Revision.ZERO, 100).next

    val archived = store.archiveBefore(LocalDate(2026, 10, 29))

    archived shouldBe 1
    val page = store.page(highest, 100)
    page.removed shouldContainExactly listOf(old)
    page.events.shouldBeEmpty()
    drain().map { it.first } shouldContainExactlyInAnyOrder listOf("Night new")
  }

  @Test
  fun `lists listings starting soon`() {
    store.upsert(listing("soon", startsAt = now + 20.hours))
    store.upsert(listing("later", startsAt = now + 60.hours))
    store.upsert(listing("past", startsAt = now - 30.hours))

    store.listingsStartingWithin(48.hours).map { it.externalId } shouldContainExactly listOf("soon")
  }

  @Test
  fun `listings whose lineup was never read need one`() {
    store.upsert(listing("read"))
    store.upsert(listing("unread"))
    store.upsert(listing("old", startsAt = Instant.parse("2026-10-27T22:00:00Z")))
    store.archiveBefore(LocalDate(2026, 10, 29))

    store.setLineup(Source.DICE, "read", emptyList())

    store.listingsNeedingLineup().map { it.externalId } shouldContainExactly listOf("unread")
  }

  @Test
  fun `every database has its own stable epoch`() {
    val first = store.page(Revision.ZERO, 10).epoch
    val other = EventStore(PostgresTestDb.freshDataSource(), clock).page(Revision.ZERO, 10).epoch

    first.isNotBlank() shouldBe true
    store.page(Revision.ZERO, 10).epoch shouldBe first
    (other == first) shouldBe false
  }

  @Test
  fun `knows the poster url of an event`() {
    val id = store.upsert(listing("a")).id

    store.posterUrl(id) shouldBe "https://img/a.jpg"
  }
}
