package fr.axllvy.timetabler.server.db

import javax.sql.DataSource

private object SchemaResource

/**
 * Applies `schema.sql`, which is idempotent and therefore safe on every boot.
 *
 * @throws IllegalStateException when the resource is missing from the jar.
 */
fun applySchema(dataSource: DataSource) {
  val sql =
    checkNotNull(SchemaResource::class.java.getResourceAsStream("/schema.sql")) {
        "schema.sql is missing from the server resources"
      }
      .bufferedReader()
      .use { it.readText() }
  dataSource.connection.use { connection ->
    connection.createStatement().use { statement -> statement.execute(sql) }
  }
}
