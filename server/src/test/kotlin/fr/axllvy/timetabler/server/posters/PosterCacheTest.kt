package fr.axllvy.timetabler.server.posters

import fr.axllvy.timetabler.domain.EventId
import fr.axllvy.timetabler.server.http.PoliteFetcher
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class PosterCacheTest {

  private val png =
    ByteArrayOutputStream()
      .also { ImageIO.write(BufferedImage(800, 800, BufferedImage.TYPE_INT_RGB), "png", it) }
      .toByteArray()

  @Test
  fun `concurrent requests for one poster fetch it once`(): Unit = runBlocking {
    val upstream = AtomicInteger()
    val fetcher =
      PoliteFetcher(
        HttpClient(
          MockEngine {
            upstream.incrementAndGet()
            delay(200)
            respond(png)
          }
        ),
        "TimeTabler/test",
      )
    val cache = PosterCache(createTempDirectory(), fetcher)

    val results = (1..4).map { async { cache.jpeg(EventId("e1"), "https://img.example/e1.png") } }.awaitAll()

    upstream.get() shouldBe 1
    results.map { it?.size }.toSet().size shouldBe 1
  }
}
