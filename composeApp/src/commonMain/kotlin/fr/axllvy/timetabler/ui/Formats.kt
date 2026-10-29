package fr.axllvy.timetabler.ui

import fr.axllvy.timetabler.domain.PARIS
import fr.axllvy.timetabler.wire.ListingDto
import fr.axllvy.timetabler.wire.ListingStatus
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.toLocalDateTime

private fun DayOfWeek.short() = name.take(3).lowercase().replaceFirstChar { it.uppercase() }

private fun Month.short() = name.take(3).lowercase().replaceFirstChar { it.uppercase() }

/** A date as "Fri 30 Oct". */
fun shortDate(date: LocalDate): String =
  "${date.dayOfWeek.short()} ${date.day} ${date.month.short()}"

/** Header of a night in the list: "Tonight · Fri 30 Oct" for tonight, else "Sat 31 Oct". */
fun nightLabel(night: LocalDate, tonight: LocalDate): String =
  if (night == tonight) "Tonight · ${shortDate(night)}" else shortDate(night)

/** Paris wall-clock time of [instant] as "23:30". */
fun clockTime(instant: Instant): String {
  val time = instant.toLocalDateTime(PARIS).time
  return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}

/** "23:30 → 07:00", or only the start when the end is unknown. */
fun hours(start: Instant, end: Instant?): String =
  if (end == null) clockTime(start) else "${clockTime(start)} → ${clockTime(end)}"

/** The first [count] artists, then "+N" for the rest. */
fun headliners(lineup: List<String>, count: Int = 3): String {
  val shown = lineup.take(count).joinToString(", ")
  val rest = lineup.size - count
  return if (rest > 0) "$shown +$rest" else shown
}

/** Badge of an event card: "cancelled" or "sold out" when every listing says so, else "lineup". */
fun eventBadge(listings: List<ListingDto>): String =
  when {
    listings.isNotEmpty() && listings.all { it.status == ListingStatus.CANCELLED } -> "cancelled"
    listings.isNotEmpty() &&
      listings.all {
        it.status == ListingStatus.SOLD_OUT || it.status == ListingStatus.CANCELLED
      } -> "sold out"
    else -> "lineup"
  }
