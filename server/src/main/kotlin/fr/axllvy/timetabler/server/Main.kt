package fr.axllvy.timetabler.server

import fr.axllvy.timetabler.server.db.applySchema
import fr.axllvy.timetabler.server.db.dataSource
import fr.axllvy.timetabler.server.dice.DiceScraper
import fr.axllvy.timetabler.server.events.EventStore
import fr.axllvy.timetabler.server.http.PoliteFetcher
import fr.axllvy.timetabler.server.posters.PosterCache
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

/** Starts the TimeTabler server with the configuration from the environment. */
fun main() {
  val config = ServerConfig.fromEnv()
  val db = dataSource(config.dbUrl, config.dbUser, config.dbPassword)
  applySchema(db)
  val store = EventStore(db)
  // DICE renders a cold event page in up to about 30 s.
  val client = HttpClient(CIO) { install(HttpTimeout) { requestTimeoutMillis = 60_000 } }
  val fetcher = PoliteFetcher(client, "TimeTabler/$BUILD_SHA (+https://github.com/Axl-Lvy/TimeTabler)")
  val scraper = DiceScraper(fetcher, store)
  embeddedServer(Netty, port = config.port) {
      timeTablerModule(store, PosterCache(config.posterDir, fetcher), config.staticDir)
      schedulingModule(scraper, store)
    }
    .start(wait = true)
}
