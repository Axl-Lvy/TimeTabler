package fr.axllvy.timetabler.server.health

import fr.axllvy.timetabler.server.dice.ScrapeReport
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * The last run of one scrape job, with the counts of its [ScrapeReport]; [failure] is null when it
 * completed, and [consecutiveFailures] counts the failed runs since the last success.
 */
@Serializable
data class ScrapeRun(
  val lastAttemptAt: Instant,
  val lastSuccessAt: Instant?,
  val consecutiveFailures: Int,
  val failure: String?,
  val pages: Int,
  val listings: Int,
  val upserted: Int,
  val lineups: Int,
  val lastDay: LocalDate?,
  val overflowedDays: Int,
  val overflowedSubGenres: Int,
)

/** This job's history after a run that ended at [at] with [report]. */
fun ScrapeRun?.after(report: ScrapeReport, at: Instant): ScrapeRun {
  val ok = report.failed == null
  return ScrapeRun(
    lastAttemptAt = at,
    lastSuccessAt = if (ok) at else this?.lastSuccessAt,
    consecutiveFailures = if (ok) 0 else (this?.consecutiveFailures ?: 0) + 1,
    failure = report.failed,
    pages = report.pages,
    listings = report.listings,
    upserted = report.upserted,
    lineups = report.lineups,
    lastDay = report.lastDay,
    overflowedDays = report.overflowedDays,
    overflowedSubGenres = report.overflowedSubGenres,
  )
}

/** Body of `GET /health`: [scrapes] maps each job to its last run, null when unreadable. */
@Serializable
data class Health(val status: String, val build: String, val scrapes: Map<String, ScrapeRun>?)
