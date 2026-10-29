package fr.axllvy.timetabler.server

import fr.axllvy.timetabler.server.http.PoliteFetcher
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import java.io.IOException

/** Reads a test resource as text. */
fun fixture(path: String): String =
  checkNotNull(object {}.javaClass.getResource("/$path")) { "missing fixture $path" }.readText()

/** A fetcher whose every request fails as if the network were down. */
fun offlineFetcher(): PoliteFetcher =
  PoliteFetcher(HttpClient(MockEngine { throw IOException("offline") }), "TimeTabler/test")
