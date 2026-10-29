package fr.axllvy.timetabler.domain

import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Time zone of every local date and time in TimeTabler. */
val PARIS: TimeZone = loadTimeZoneDatabase().let { TimeZone.of("Europe/Paris") }

/** Makes the IANA time zone database available on platforms that do not bundle one. */
internal expect fun loadTimeZoneDatabase()

private val NIGHT_SHIFT = 6.hours

/** Date of the night an event starting at [start] belongs to, so a 00:30 start counts as the evening before. */
fun nightDateOf(start: Instant): LocalDate = (start - NIGHT_SHIFT).toLocalDateTime(PARIS).date

/** Date of the night in progress at [now]. */
fun tonight(now: Instant): LocalDate = nightDateOf(now)
