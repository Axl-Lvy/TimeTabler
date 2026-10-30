package fr.axllvy.timetabler.server

import io.kotest.matchers.shouldBe
import javax.sql.DataSource
import kotlin.test.Test

class PostgresTestDbTest {

  private fun DataSource.backendsOf(database: String): Int = connection.use { c ->
    c.prepareStatement("SELECT count(*) FROM pg_stat_activity WHERE datname = ?").use {
      it.setString(1, database)
      it.executeQuery().use { rs -> rs.next().let { rs.getInt(1) } }
    }
  }

  @Test
  fun `a closed test database keeps no connection open`() {
    val db = PostgresTestDb.freshDataSource()
    val name = db.connection.use { it.catalog }

    db.close()

    PostgresTestDb.freshDataSource().use { other ->
      // A backend leaves pg_stat_activity shortly after its socket closes.
      var open = other.backendsOf(name)
      repeat(20) {
        if (open == 0) return@repeat
        Thread.sleep(100)
        open = other.backendsOf(name)
      }
      open shouldBe 0
    }
  }
}
