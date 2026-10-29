package fr.axllvy.timetabler.list

import fr.axllvy.timetabler.domain.EventId
import fr.axllvy.timetabler.domain.PARIS
import fr.axllvy.timetabler.domain.Revision
import fr.axllvy.timetabler.domain.nightDateOf
import fr.axllvy.timetabler.wire.EventDto
import fr.axllvy.timetabler.wire.VenueDto
import io.kotest.matchers.collections.shouldContainExactly
import kotlin.test.Test
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant

class EventFiltersTest {

  private fun paris(iso: String) = LocalDateTime.parse(iso).toInstant(PARIS)

  private fun event(
    title: String,
    start: String,
    venue: String = "Rex Club",
    lineup: List<String> = emptyList(),
  ): EventDto {
    val startsAt = paris(start)
    return EventDto(
      EventId(title),
      Revision(1),
      title,
      nightDateOf(startsAt),
      startsAt,
      null,
      VenueDto(venue, null),
      lineup,
      null,
      emptyList(),
    )
  }

  private val thursday = event("Thursday", "2026-10-29T23:00")
  private val fridayLate = event("Friday late", "2026-10-31T00:30")
  private val friday = event("Friday", "2026-10-30T23:00")
  private val saturday = event("Saturday", "2026-10-31T23:30")
  private val nextFriday = event("Next friday", "2026-11-06T23:00")
  private val all = listOf(nextFriday, saturday, fridayLate, friday, thursday)

  private fun titles(groups: List<NightGroup>) = groups.flatMap { g -> g.events.map { it.title } }

  @Test
  fun `tonight after midnight still shows friday night`() {
    val groups = filterEvents(all, "", QuickFilter.TONIGHT, paris("2026-10-31T03:00"))

    groups.map { it.night } shouldContainExactly listOf(LocalDate(2026, 10, 30))
    titles(groups) shouldContainExactly listOf("Friday", "Friday late")
  }

  @Test
  fun `weekend from a wednesday is the coming friday and saturday`() {
    val groups = filterEvents(all, "", QuickFilter.WEEKEND, paris("2026-10-28T12:00"))

    groups.map { it.night } shouldContainExactly
      listOf(LocalDate(2026, 10, 30), LocalDate(2026, 10, 31))
  }

  @Test
  fun `weekend on saturday night keeps friday and saturday`() {
    val groups = filterEvents(all, "", QuickFilter.WEEKEND, paris("2026-10-31T03:00"))

    groups.map { it.night } shouldContainExactly
      listOf(LocalDate(2026, 10, 30), LocalDate(2026, 10, 31))
  }

  @Test
  fun `weekend from a sunday is the next one`() {
    val groups = filterEvents(all, "", QuickFilter.WEEKEND, paris("2026-11-01T15:00"))

    groups.map { it.night } shouldContainExactly listOf(LocalDate(2026, 11, 6))
  }

  @Test
  fun `past nights are never shown`() {
    val groups = filterEvents(all, "", QuickFilter.ALL, paris("2026-10-30T12:00"))

    titles(groups) shouldContainExactly listOf("Friday", "Friday late", "Saturday", "Next friday")
  }

  @Test
  fun `search ignores accents and case across title venue and lineup`() {
    val events =
      listOf(
        event("Possession", "2026-10-30T23:00", venue = "La Machine du Moulin Rouge"),
        event("Halloween", "2026-10-30T23:00", lineup = listOf("Clara Cuvé")),
        event("Other", "2026-10-30T23:00"),
      )
    val now = paris("2026-10-30T12:00")

    titles(filterEvents(events, "cuve", QuickFilter.ALL, now)) shouldContainExactly
      listOf("Halloween")
    titles(filterEvents(events, "MOULIN", QuickFilter.ALL, now)) shouldContainExactly
      listOf("Possession")
    titles(filterEvents(events, "possession", QuickFilter.ALL, now)) shouldContainExactly
      listOf("Possession")
  }
}
