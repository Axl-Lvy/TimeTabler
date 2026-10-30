package fr.axllvy.timetabler.server.shotgun

import fr.axllvy.timetabler.server.events.ScrapedListing
import fr.axllvy.timetabler.server.fixture
import fr.axllvy.timetabler.wire.ListingStatus
import fr.axllvy.timetabler.wire.Source
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

class ShotgunEventParserTest {

  private val page = fixture("shotgun/event-page.html")
  private val eventBlock = Regex(""""@type": "MusicEvent".*?</script>""")

  private fun withEvent(transform: (String) -> String) =
    page.replace(eventBlock) { transform(it.value) }

  @Test
  fun `reads the event and its performers from JSON-LD`() {
    val listing = ShotgunEventParser.listing(page, "dusk-mia-mao")

    listing shouldBe
      ScrapedListing(
        source = Source.SHOTGUN,
        externalId = "dusk-mia-mao",
        url = "https://shotgun.live/fr/events/dusk-mia-mao",
        title = "Dusk: P.E.A.R.L. B2b Not A Headliner, Againstme (Live) &More",
        startsAt = Instant.parse("2025-07-26T21:59:00Z"),
        endsAt = Instant.parse("2025-07-27T05:00:00Z"),
        venueName = "Mia Mao",
        venueAddress = "12/A Rue Ella Fitzgerald, 75019 Paris, France",
        status = ListingStatus.ON_SALE,
        posterUrl =
          "https://assets.shotgun.live/image/upload/c_limit,w_1200,h_630,f_jpg,q_auto/production/artworks/Banner_Shotgun_1920x1080_zj93sl.png",
        lineup =
          listOf(
            "AgainstMe",
            "Not A Headliner",
            "P.E.A.R.L.",
            "Danya",
            "Vera Grace",
            "SAMA",
            "Dusk Records",
          ),
      )
  }

  @Test
  fun `a cancelled event is cancelled`() {
    val html = withEvent { it.replace("EventScheduled", "EventCancelled") }

    ShotgunEventParser.listing(html, "dusk-mia-mao")?.status shouldBe ListingStatus.CANCELLED
  }

  @Test
  fun `an event whose every offer is sold out is sold out`() {
    val offers = """"offers": [{"@type": "Offer", "availability": "https://schema.org/SoldOut"}]"""
    val html = withEvent { it.replace(""""offers": []""", offers) }

    ShotgunEventParser.listing(html, "dusk-mia-mao")?.status shouldBe ListingStatus.SOLD_OUT
  }

  @Test
  fun `an event without performers has an empty lineup`() {
    val html = withEvent { it.replace(Regex(""""performer": \[.*?],"""), "") }

    ShotgunEventParser.listing(html, "dusk-mia-mao").shouldNotBeNull().lineup shouldBe emptyList()
  }

  @Test
  fun `an event without a start is skipped`() {
    val html = withEvent { it.replace(Regex(""""startDate": "[^"]*","""), "") }

    ShotgunEventParser.listing(html, "dusk-mia-mao").shouldBeNull()
  }

  @Test
  fun `a page without event data gives null`() {
    ShotgunEventParser.listing(fixture("shotgun/event-not-found.html"), "gone").shouldBeNull()
  }
}
