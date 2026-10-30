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
import kotlin.time.Duration.Companion.milliseconds

private fun fetcherUserAgent() = "TimeTabler/$BUILD_SHA (+https://github.com/Axl-Lvy/TimeTabler)"

/** Starts the TimeTabler server with the configuration from the environment. */
fun main() {
  val config = ServerConfig.fromEnv()
  val db = dataSource(config.dbUrl, config.dbUser, config.dbPassword)
  applySchema(db)
  val store = EventStore(db)
  // DICE renders a cold event page in up to about 30 s.
  val client = HttpClient(CIO) { install(HttpTimeout) { requestTimeoutMillis = 60_000 } }
  val fetcher = PoliteFetcher(client, fetcherUserAgent())
  val scraper = DiceScraper(fetcher, store)
  // Posters come from image CDNs built for page loads, so they get a shorter gap than pages.
  val posterFetcher = PoliteFetcher(client, fetcherUserAgent(), minInterval = 250.milliseconds)
  val posters = PosterCache(config.posterDir, posterFetcher)
  embeddedServer(Netty, port = config.port) {
      timeTablerModule(store, posters, config.staticDir)
      schedulingModule(ScrapeJobs(scraper, store, posters), store)
    }
    .start(wait = true)
}
