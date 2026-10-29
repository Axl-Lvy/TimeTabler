package fr.axllvy.timetabler.ui

import fr.axllvy.timetabler.wire.ListingDto
import fr.axllvy.timetabler.wire.ListingStatus
import fr.axllvy.timetabler.wire.Source
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class FormatsTest {

  @Test
  fun `labels tonight and other nights`() {
    nightLabel(LocalDate(2026, 10, 30), tonight = LocalDate(2026, 10, 30)) shouldBe
      "Tonight · Fri 30 Oct"
    nightLabel(LocalDate(2026, 10, 31), tonight = LocalDate(2026, 10, 30)) shouldBe "Sat 31 Oct"
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

  private fun listing(status: ListingStatus) = ListingDto(Source.DICE, "https://dice.fm/event/x", status)

  @Test
  fun `badges a cancelled or sold out event`() {
    eventBadge(listOf(listing(ListingStatus.CANCELLED))) shouldBe "cancelled"
    eventBadge(listOf(listing(ListingStatus.SOLD_OUT), listing(ListingStatus.CANCELLED))) shouldBe "sold out"
    eventBadge(listOf(listing(ListingStatus.SOLD_OUT), listing(ListingStatus.ON_SALE))) shouldBe "lineup"
    eventBadge(emptyList()) shouldBe "lineup"
  }
}
