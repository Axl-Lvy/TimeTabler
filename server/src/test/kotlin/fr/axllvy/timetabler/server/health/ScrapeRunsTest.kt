package fr.axllvy.timetabler.server.health

import fr.axllvy.timetabler.server.PostgresTestDb
import fr.axllvy.timetabler.server.dice.ScrapeReport
import fr.axllvy.timetabler.server.events.EventStore
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class ScrapeRunsTest {

  private val db = PostgresTestDb.freshDataSource()
  private val store = EventStore(db)
  private val at = Instant.parse("2026-11-07T03:00:00Z")

  @AfterTest
  fun close() {
    db.close()
  }

  @Test
  fun `runs saved by an earlier process are read back`() = runTest {
    val before = ScrapeRuns(store)
    before.record("agenda", ScrapeReport(183, 4, null), at)
    val after = ScrapeRuns(store)

    after.load()

    after.current() shouldBe before.current()
  }

  @Test
  fun `a failure after a restart counts the failures before it`() = runTest {
    ScrapeRuns(store).record("refresh", ScrapeReport(0, 0, "429"), at)
    val restarted = ScrapeRuns(store)

    restarted.record("refresh", ScrapeReport(0, 0, "429"), at)

    restarted.current()!!.getValue("refresh").consecutiveFailures shouldBe 2
  }

  @Test
  fun `an unreadable saved run is skipped`() = runTest {
    ScrapeRuns(store).record("agenda", ScrapeReport(183, 4, null), at)
    db.connection.use {
      it
        .createStatement()
        .execute("INSERT INTO server_meta (key, value) VALUES ('scrape.old', '{}')")
    }
    val runs = ScrapeRuns(store)

    runs.load()

    runs.current()!!.keys shouldBe setOf("agenda")
  }
}
