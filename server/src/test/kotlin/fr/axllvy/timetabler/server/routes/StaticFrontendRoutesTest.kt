package fr.axllvy.timetabler.server.routes

import fr.axllvy.timetabler.server.PostgresTestDb
import fr.axllvy.timetabler.server.events.EventStore
import fr.axllvy.timetabler.server.offlineFetcher
import fr.axllvy.timetabler.server.posters.PosterCache
import fr.axllvy.timetabler.server.timeTablerModule
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.testApplication
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test

class StaticFrontendRoutesTest {

  private val dir =
    createTempDirectory().also {
      it.resolve("index.html").writeText("<html></html>")
      it.resolve("composeApp.0123456789abcdef0123.js").writeText("js")
    }

  private fun app(test: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) =
    testApplication {
      application {
        timeTablerModule(
          EventStore(PostgresTestDb.freshDataSource()),
          PosterCache(createTempDirectory(), offlineFetcher()),
          staticDir = dir.toFile(),
        )
      }
      test()
    }

  @Test
  fun `index is revalidated on every load`() = app {
    client.get("/").headers[HttpHeaders.CacheControl] shouldBe "no-cache"
  }

  @Test
  fun `hashed bundle files are cached for a year`() = app {
    client
      .get("/composeApp.0123456789abcdef0123.js")
      .headers[HttpHeaders.CacheControl]!! shouldContain "max-age=31536000"
  }

  @Test
  fun `every response isolates the origin for wasm`() = app {
    for (path in listOf("/", "/health", "/v1/events")) {
      val response = client.get(path)
      response.headers["Cross-Origin-Opener-Policy"] shouldBe "same-origin"
      response.headers["Cross-Origin-Embedder-Policy"] shouldBe "credentialless"
    }
  }
}
