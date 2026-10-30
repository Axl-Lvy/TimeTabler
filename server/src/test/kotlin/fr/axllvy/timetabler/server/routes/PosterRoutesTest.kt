package fr.axllvy.timetabler.server.routes

import fr.axllvy.timetabler.server.PostgresTestDb
import fr.axllvy.timetabler.server.events.EventStore
import fr.axllvy.timetabler.server.events.ScrapedListing
import fr.axllvy.timetabler.server.http.PoliteFetcher
import fr.axllvy.timetabler.server.posters.PosterCache
import fr.axllvy.timetabler.server.timeTablerModule
import fr.axllvy.timetabler.wire.ListingStatus
import fr.axllvy.timetabler.wire.Source
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.server.testing.testApplication
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking

class PosterRoutesTest {

  private val db = PostgresTestDb.freshDataSource()
  private val store = EventStore(db)
  private var sourceRequests = 0
  private val png =
    ByteArrayOutputStream()
      .also { ImageIO.write(BufferedImage(1200, 1200, BufferedImage.TYPE_INT_ARGB), "png", it) }
      .toByteArray()
  private val fetcher =
    PoliteFetcher(
      HttpClient(
        MockEngine {
          sourceRequests++
          respond(
            png,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Image.PNG.toString()),
          )
        }
      ),
      "TimeTabler/test",
    )

  private val eventId = runBlocking {
    store
      .upsert(
        ScrapedListing(
          Source.DICE,
          "a",
          "https://dice.fm/event/a",
          "Night",
          Instant.parse("2026-10-30T22:30:00Z"),
          null,
          "Rex Club",
          null,
          ListingStatus.ON_SALE,
          "https://img.example/a.png",
          emptyList(),
        )
      )
      .id
  }

  @AfterTest
  fun close() {
    db.close()
  }

  private fun app(test: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) =
    testApplication {
      application {
        timeTablerModule(store, PosterCache(createTempDirectory(), fetcher), staticDir = null)
      }
      test()
    }

  @Test
  fun `serves a resized jpeg and fetches the source once`() = app {
    val first = client.get("/v1/posters/${eventId.value}")
    val second = client.get("/v1/posters/${eventId.value}")

    first.status shouldBe HttpStatusCode.OK
    first.headers[HttpHeaders.ContentType] shouldBe "image/jpeg"
    ImageIO.read(first.readRawBytes().inputStream()).width shouldBeLessThanOrEqual 600
    second.status shouldBe HttpStatusCode.OK
    sourceRequests shouldBe 1
  }

  @Test
  fun `an unknown event has no poster`() = app {
    client.get("/v1/posters/00000000-0000-0000-0000-000000000000").status shouldBe
      HttpStatusCode.NotFound
    client.get("/v1/posters/not-a-uuid").status shouldBe HttpStatusCode.NotFound
  }
}
