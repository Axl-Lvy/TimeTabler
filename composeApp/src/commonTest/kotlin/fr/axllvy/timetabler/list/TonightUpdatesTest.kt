package fr.axllvy.timetabler.list

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TonightUpdatesTest {

  private fun TestScope.clockFrom(start: Instant) =
    object : Clock {
      override fun now() = start + testScheduler.currentTime.milliseconds
    }

  private fun TestScope.collectFor(start: Instant, hours: Int): List<LocalDate> {
    val nights = mutableListOf<LocalDate>()
    val job = tonightUpdates(clockFrom(start)).onEach { nights += it }.launchIn(this)
    testScheduler.advanceTimeBy(hours.hours)
    job.cancel()
    return nights
  }

  @Test
  fun `the night the clocks go forward rolls over within a minute`() = runTest {
    // Saturday 28 March 2026 at 22:00 CET; the night ends at 07:00 CEST, 05:00Z.
    val start = Instant.parse("2026-03-28T21:00:00Z")

    val nights = collectFor(start, hours = 9)

    nights shouldContainExactly listOf(LocalDate(2026, 3, 28), LocalDate(2026, 3, 29))
  }

  @Test
  fun `the night the clocks go back rolls over within a minute`() = runTest {
    // Saturday 24 October 2026 at 22:00 CEST; the night ends at 05:00 CET, 04:00Z.
    val start = Instant.parse("2026-10-24T20:00:00Z")

    val nights = collectFor(start, hours = 9)

    nights shouldContainExactly listOf(LocalDate(2026, 10, 24), LocalDate(2026, 10, 25))
  }

  @Test
  fun `a woken device sees the new night within a minute`() = runTest {
    val start = Instant.parse("2026-10-30T22:00:00Z")
    var sleptFor = 0.hours
    val clock =
      object : Clock {
        override fun now() = start + sleptFor + testScheduler.currentTime.milliseconds
      }
    val nights = mutableListOf<LocalDate>()
    val job = tonightUpdates(clock).onEach { nights += it }.launchIn(this)
    testScheduler.runCurrent()

    sleptFor = 12.hours
    testScheduler.advanceTimeBy(1.minutes + 1.milliseconds)
    job.cancel()

    nights shouldBe listOf(LocalDate(2026, 10, 30), LocalDate(2026, 10, 31))
  }
}
