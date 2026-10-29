package fr.axllvy.timetabler.server.dice

import fr.axllvy.timetabler.server.fixture
import fr.axllvy.timetabler.wire.ListingStatus
import fr.axllvy.timetabler.wire.Source
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class DiceBrowseParserTest {

  @Test
  fun `parses every event of a day page`() {
    val page = DiceBrowseParser.parse(fixture("dice/day-page.html"))!!

    page.listings shouldHaveSize 5
    page.overflow shouldBe false
    page.listings.map { it.source }.toSet() shouldBe setOf(Source.DICE)
  }

  @Test
  fun `maps the fields of a listing`() {
    val first = DiceBrowseParser.parse(fixture("dice/day-page.html"))!!.listings.first()

    first.externalId shouldBe "6ad5f2180594730001f32ed4"
    first.title shouldBe "Kouss Kouss - Twerkistan + DJ set"
    first.url shouldBe "https://dice.fm/event/6ad5f2180594730001f32ed4"
    first.startsAt shouldBe Instant.parse("2026-11-07T19:00:00Z")
    first.endsAt shouldBe Instant.parse("2026-11-07T23:00:00Z")
    first.venueName shouldBe "Ground Control Gare de Lyon"
    first.venueAddress shouldBe "81 Rue du Charolais, 75012 Paris, France"
    first.status shouldBe ListingStatus.ON_SALE
    first.posterUrl shouldBe
      "https://dice-media.imgix.net/attachments/2026-10-19/5f5ba329-0d20-425a-ac55-3fd4368fbcf4.jpg?rect=0%2C0%2C1080%2C1080"
    first.lineup shouldBe emptyList()
  }

  @Test
  fun `skips an event without dates`() {
    val page = DiceBrowseParser.parse(fixture("dice/day-page-bad-event.html"))!!

    page.listings shouldHaveSize 4
    page.listings.map { it.externalId } shouldNotContain "6ae30f3eff6a4e000149d810"
  }

  @Test
  fun `skips an event with a malformed date`() {
    val html =
      fixture("dice/day-page.html")
        .replace("\"event_start_date\":\"2026-11-07T20:00:00+01:00\"", "\"event_start_date\":\"TBA\"")

    val page = DiceBrowseParser.parse(html)!!

    page.listings shouldHaveSize 4
  }

  @Test
  fun `a page without page data is not a page`() {
    DiceBrowseParser.parse("<html><body>Checking your browser</body></html>").shouldBeNull()
  }

  @Test
  fun `flags a page with more events than it shows`() {
    val page = DiceBrowseParser.parse(fixture("dice/day-page-overflow.html"))!!

    page.overflow shouldBe true
    page.listings shouldHaveSize 22
  }

  @Test
  fun `maps sold out status`() {
    val page = DiceBrowseParser.parse(fixture("dice/day-page-overflow.html"))!!

    page.listings.count { it.status == ListingStatus.SOLD_OUT } shouldBe 2
  }

  @Test
  fun `builds a one day url for a category`() {
    DiceBrowseParser.dayUrl("dj", LocalDate(2026, 11, 7)) shouldBe
      "https://dice.fm/browse/paris-5b23e8a0e63cc224a4c36a2d/music/dj?from=2026-11-07&until=2026-11-07"
  }
}
