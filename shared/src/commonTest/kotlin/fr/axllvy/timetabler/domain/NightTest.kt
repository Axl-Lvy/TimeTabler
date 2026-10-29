package fr.axllvy.timetabler.domain

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant

class NightTest {

  private fun paris(iso: String) = LocalDateTime.parse(iso).toInstant(PARIS)

  @Test
  fun `evening start belongs to the same date`() {
    nightDateOf(paris("2026-10-30T23:30")) shouldBe LocalDate(2026, 10, 30)
  }

  @Test
  fun `after midnight start belongs to the previous date`() {
    nightDateOf(paris("2026-10-31T00:30")) shouldBe LocalDate(2026, 10, 30)
    nightDateOf(paris("2026-10-31T05:59")) shouldBe LocalDate(2026, 10, 30)
  }

  @Test
  fun `six in the morning starts a new night`() {
    nightDateOf(paris("2026-10-31T06:00")) shouldBe LocalDate(2026, 10, 31)
  }

  @Test
  fun `start in the repeated DST hour stays on the saturday night`() {
    // 02:30 CET, the second 02:30 after clocks go back.
    val secondTwoThirty = Instant.parse("2026-10-25T01:30:00Z")

    nightDateOf(secondTwoThirty) shouldBe LocalDate(2026, 10, 24)
  }

  @Test
  fun `tonight at three on saturday is friday`() {
    tonight(paris("2026-10-31T03:00")) shouldBe LocalDate(2026, 10, 30)
  }
}
