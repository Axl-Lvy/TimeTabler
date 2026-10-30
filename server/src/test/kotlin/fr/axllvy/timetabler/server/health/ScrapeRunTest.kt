package fr.axllvy.timetabler.server.health

import fr.axllvy.timetabler.server.dice.ScrapeReport
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class ScrapeRunTest {

  private val at = Instant.parse("2026-11-07T03:00:00Z")
  private val ok =
    ScrapeReport(pages = 183, upserted = 12, failed = null, lineups = 9, listings = 400)
      .copy(lastDay = LocalDate(2027, 1, 6))

  @Test
  fun `a first successful run is its own last success`() {
    val run = null.after(ok, at)

    run shouldBe
      ScrapeRun(
        lastAttemptAt = at,
        lastSuccessAt = at,
        consecutiveFailures = 0,
        failure = null,
        pages = 183,
        listings = 400,
        upserted = 12,
        lineups = 9,
        lastDay = LocalDate(2027, 1, 6),
        overflowedDays = 0,
        overflowedSubGenres = 0,
      )
  }

  @Test
  fun `a failed run keeps the last success and counts failures in a row`() {
    val first = null.after(ok, at)
    val failed = ok.copy(failed = "429 Too Many Requests")

    val run = first.after(failed, at + 24.hours).after(failed, at + 48.hours)

    run.lastAttemptAt shouldBe at + 48.hours
    run.lastSuccessAt shouldBe at
    run.consecutiveFailures shouldBe 2
    run.failure shouldBe "429 Too Many Requests"
  }

  @Test
  fun `a success after failures resets the count`() {
    val failed = null.after(ok.copy(failed = "timeout"), at)

    val run = failed.after(ok, at + 24.hours)

    run.lastSuccessAt shouldBe at + 24.hours
    run.consecutiveFailures shouldBe 0
    run.failure shouldBe null
  }
}
