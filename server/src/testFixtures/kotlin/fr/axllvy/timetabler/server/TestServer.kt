package fr.axllvy.timetabler.server

import fr.axllvy.timetabler.server.events.EventStore
import fr.axllvy.timetabler.server.health.ScrapeRuns
import fr.axllvy.timetabler.server.http.PoliteFetcher
import fr.axllvy.timetabler.server.posters.PosterCache
import io.ktor.client.HttpClient
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.nio.file.Files
import kotlinx.coroutines.runBlocking

/** The TimeTabler server on a random local port, backed by a fresh test database. */
class TestServer : AutoCloseable {

  private val db = PostgresTestDb.freshDataSource()

  /** Store behind the running server, for arranging data. */
  val store = EventStore(db)

  private val server: EmbeddedServer<*, *> =
    embeddedServer(Netty, port = 0) {
        val posters =
          PosterCache(
            Files.createTempDirectory("posters"),
            PoliteFetcher(HttpClient(), "TimeTabler/test"),
          )
        timeTablerModule(store, posters, ScrapeRuns(store), staticDir = null)
      }
      .start(wait = false)

  /** Base URL of the running server, without a trailing slash. */
  val baseUrl: String = runBlocking {
    "http://localhost:${server.engine.resolvedConnectors().first().port}"
  }

  override fun close() {
    server.stop(gracePeriodMillis = 0, timeoutMillis = 1000)
    db.close()
  }
}
