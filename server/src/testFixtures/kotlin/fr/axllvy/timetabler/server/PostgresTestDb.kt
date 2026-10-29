package fr.axllvy.timetabler.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import fr.axllvy.timetabler.server.db.applySchema
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import org.testcontainers.containers.PostgreSQLContainer

/** One Postgres container for the whole test run, handing each caller its own empty database. */
object PostgresTestDb {

  private val container by lazy {
    PostgreSQLContainer("postgres:16-alpine").apply {
      withReuse(false)
      // Every test class opens its own small pool and the run never closes them.
      withCommand("postgres", "-c", "max_connections=1000")
      start()
    }
  }

  private val counter = AtomicInteger()

  /** A pooled data source on a new database with the schema applied. */
  fun freshDataSource(): DataSource {
    val name = "tt_${counter.incrementAndGet()}"
    container.createConnection("").use { it.createStatement().execute("CREATE DATABASE $name") }
    return HikariDataSource(
        HikariConfig().apply {
          jdbcUrl = container.jdbcUrl.replaceAfterLast('/', name)
          username = container.username
          password = container.password
          maximumPoolSize = 2
          minimumIdle = 0
        }
      )
      .also { applySchema(it) }
  }
}
