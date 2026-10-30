package fr.axllvy.timetabler.ui

import androidx.compose.runtime.Composable
import fr.axllvy.timetabler.domain.PARIS
import fr.axllvy.timetabler.wire.ListingDto
import fr.axllvy.timetabler.wire.ListingStatus
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import timetabler.composeapp.generated.resources.Res
import timetabler.composeapp.generated.resources.badge_cancelled
import timetabler.composeapp.generated.resources.badge_lineup
import timetabler.composeapp.generated.resources.badge_sold_out
import timetabler.composeapp.generated.resources.day_names_short
import timetabler.composeapp.generated.resources.month_names_short
import timetabler.composeapp.generated.resources.night_tonight

/** Abbreviated day and month names of one language, Monday and January first. */
class DateNames(val days: List<String>, val months: List<String>)

/** Date names in the device language. */
@Composable
fun dateNames(): DateNames =
  DateNames(
    stringArrayResource(Res.array.day_names_short),
    stringArrayResource(Res.array.month_names_short),
  )

/** A date as "Fri 30 Oct" in [names]; names still loading on the web are left out. */
fun shortDate(date: LocalDate, names: DateNames): String =
  listOfNotNull(
      names.days.getOrNull(date.dayOfWeek.ordinal),
      date.day.toString(),
      names.months.getOrNull(date.month.ordinal),
    )
    .joinToString(" ")

/** Header of a night in the list: "Tonight · Fri 30 Oct" for tonight, else "Sat 31 Oct". */
@Composable
fun nightLabel(night: LocalDate, tonight: LocalDate): String {
  val date = shortDate(night, dateNames())
  return if (night == tonight) stringResource(Res.string.night_tonight, date) else date
}

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

/** Badge of an event card, with its [label]. */
enum class EventBadge(val label: StringResource) {
  LINEUP(Res.string.badge_lineup),
  SOLD_OUT(Res.string.badge_sold_out),
  CANCELLED(Res.string.badge_cancelled),
}

/** Badge of an event card: cancelled or sold out when every listing says so, else lineup. */
fun eventBadge(listings: List<ListingDto>): EventBadge =
  when {
    listings.isNotEmpty() && listings.all { it.status == ListingStatus.CANCELLED } ->
      EventBadge.CANCELLED
    listings.isNotEmpty() &&
      listings.all {
        it.status == ListingStatus.SOLD_OUT || it.status == ListingStatus.CANCELLED
      } -> EventBadge.SOLD_OUT
    else -> EventBadge.LINEUP
  }
