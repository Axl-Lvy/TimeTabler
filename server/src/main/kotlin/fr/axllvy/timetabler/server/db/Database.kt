package fr.axllvy.timetabler.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A connection pool on the given Postgres database. */
fun dataSource(url: String, user: String, password: String): DataSource =
  HikariDataSource(
    HikariConfig().apply {
      jdbcUrl = url
      username = user
      this.password = password
      maximumPoolSize = 4
    }
  )

/**
 * Runs [block] in one transaction on [Dispatchers.IO], committing on success and rolling back on
 * any exception.
 */
suspend fun <T> DataSource.transaction(block: (Connection) -> T): T =
  withContext(Dispatchers.IO) {
    connection.use { connection ->
      connection.autoCommit = false
      try {
        block(connection).also { connection.commit() }
      } catch (e: Throwable) {
        connection.rollback()
        throw e
      }
    }
  }
