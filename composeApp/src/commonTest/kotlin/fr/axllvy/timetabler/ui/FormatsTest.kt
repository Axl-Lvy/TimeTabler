package fr.axllvy.timetabler.ui

import fr.axllvy.timetabler.wire.ListingDto
import fr.axllvy.timetabler.wire.ListingStatus
import fr.axllvy.timetabler.wire.Source
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class FormatsTest {

  private val english =
    DateNames(
      days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
      months =
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
    )

  @Test
  fun `formats a date with the given names`() {
    shortDate(LocalDate(2026, 10, 30), english) shouldBe "Fri 30 Oct"
    shortDate(LocalDate(2026, 3, 1), english) shouldBe "Sun 1 Mar"
  }

  @Test
  fun `formats a date without names while they load`() {
    shortDate(LocalDate(2026, 10, 30), DateNames(emptyList(), emptyList())) shouldBe "30"
  }

  @Test
  fun `shows paris hours across midnight`() {
    hours(Instant.parse("2026-10-30T22:30:00Z"), Instant.parse("2026-10-31T06:00:00Z")) shouldBe
      "23:30 → 07:00"
    hours(Instant.parse("2026-10-30T22:30:00Z"), null) shouldBe "23:30"
  }

  @Test
  fun `shortens long lineups`() {
    headliners(listOf("A", "B", "C", "D", "E")) shouldBe "A, B, C +2"
    headliners(listOf("A", "B")) shouldBe "A, B"
  }

  private fun listing(status: ListingStatus) =
    ListingDto(Source.DICE, "https://dice.fm/event/x", status)

  @Test
  fun `badges a cancelled or sold out event`() {
    eventBadge(listOf(listing(ListingStatus.CANCELLED))) shouldBe EventBadge.CANCELLED
    eventBadge(listOf(listing(ListingStatus.SOLD_OUT), listing(ListingStatus.CANCELLED))) shouldBe
      EventBadge.SOLD_OUT
    eventBadge(listOf(listing(ListingStatus.SOLD_OUT), listing(ListingStatus.ON_SALE))) shouldBe
      EventBadge.LINEUP
    eventBadge(emptyList()) shouldBe EventBadge.LINEUP
  }
}
