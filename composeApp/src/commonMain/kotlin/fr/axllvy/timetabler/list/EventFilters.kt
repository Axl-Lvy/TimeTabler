package fr.axllvy.timetabler.list

import fr.axllvy.timetabler.domain.normalizeText
import fr.axllvy.timetabler.domain.tonight
import fr.axllvy.timetabler.wire.EventDto
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Quick filters of the events list. */
enum class QuickFilter {
  ALL,
  TONIGHT,
  WEEKEND,
}

/** Events of one night, in start order. */
data class NightGroup(val night: LocalDate, val events: List<EventDto>)

/**
 * Upcoming events matching [query] and [filter] at [now], grouped by night in date order. Nights
 * before tonight are dropped. The weekend is the Friday and Saturday nights of the current weekend,
 * or of the next one from Sunday to Thursday.
 */
fun filterEvents(events: List<EventDto>, query: String, filter: QuickFilter, now: Instant): List<NightGroup> {
  val tonight = tonight(now)
  val nights = selectedNights(filter, tonight)
  val terms = normalizeText(query).split(' ').filter { it.isNotEmpty() }
  return events
    .filter { nights(it.nightDate) && matches(it, terms) }
    .groupBy { it.nightDate }
    .entries
    .sortedBy { it.key }
    .map { (night, group) -> NightGroup(night, group.sortedBy { it.startsAt }) }
}

private fun selectedNights(filter: QuickFilter, tonight: LocalDate): (LocalDate) -> Boolean =
  when (filter) {
    QuickFilter.ALL -> { night -> night >= tonight }
    QuickFilter.TONIGHT -> { night -> night == tonight }
    QuickFilter.WEEKEND -> {
      val friday = weekendFriday(tonight)
      val weekend = setOf(friday, friday.plus(DatePeriod(days = 1)))
      return { night -> night >= tonight && night in weekend }
    }
  }

private fun weekendFriday(tonight: LocalDate): LocalDate {
  if (tonight.dayOfWeek == DayOfWeek.SATURDAY) return tonight.minus(DatePeriod(days = 1))
  var day = tonight
  while (day.dayOfWeek != DayOfWeek.FRIDAY) day = day.plus(DatePeriod(days = 1))
  return day
}

private fun matches(event: EventDto, terms: List<String>): Boolean {
  if (terms.isEmpty()) return true
  val haystack = normalizeText((listOf(event.title, event.venue.name) + event.lineup).joinToString(" "))
  return terms.all { it in haystack }
}
