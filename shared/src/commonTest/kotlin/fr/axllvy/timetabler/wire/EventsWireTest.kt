package fr.axllvy.timetabler.wire

import fr.axllvy.timetabler.domain.EventId
import fr.axllvy.timetabler.domain.Revision
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class EventsWireTest {

  private val page =
    EventsPage(
      events =
        listOf(
          EventDto(
            id = EventId("e1"),
            revision = Revision(7),
            title = "Possession",
            nightDate = LocalDate(2026, 10, 30),
            startsAt = Instant.parse("2026-10-30T22:30:00Z"),
            endsAt = null,
            venue = VenueDto("La Machine du Moulin Rouge", "90 Boulevard de Clichy"),
            lineup = listOf("I Hate Models", "Narciss", "Kmyle"),
            posterPath = "/v1/posters/e1",
            listings =
              listOf(
                ListingDto(Source.DICE, "https://dice.fm/event/x", ListingStatus.ON_SALE),
                ListingDto(Source.SHOTGUN, "https://shotgun.live/fr/events/x", ListingStatus.SOLD_OUT),
              ),
          )
        ),
      removed = listOf(EventId("old")),
      next = Revision(7),
      hasMore = false,
    )

  @Test
  fun `page survives a round trip`() {
    val json = WIRE_JSON.encodeToString(EventsPage.serializer(), page)

    WIRE_JSON.decodeFromString(EventsPage.serializer(), json) shouldBe page
  }

  @Test
  fun `unknown fields are ignored`() {
    val json =
      """{"events":[],"removed":[],"next":3,"hasMore":true,"addedLater":{"x":1}}"""

    WIRE_JSON.decodeFromString(EventsPage.serializer(), json) shouldBe
      EventsPage(emptyList(), emptyList(), Revision(3), true)
  }
}
