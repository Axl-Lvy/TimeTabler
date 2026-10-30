package fr.axllvy.timetabler.server.posters

import fr.axllvy.timetabler.domain.EventId
import fr.axllvy.timetabler.server.events.PosterRef
import fr.axllvy.timetabler.server.http.PoliteFetcher
import fr.axllvy.timetabler.wire.THEME_BACKGROUND_RGB
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.listDirectoryEntries
import kotlin.math.abs
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class PosterCacheTest {

  private fun png(type: Int = BufferedImage.TYPE_INT_RGB) =
    ByteArrayOutputStream()
      .also { ImageIO.write(BufferedImage(800, 800, type), "png", it) }
      .toByteArray()

  private val upstream = AtomicInteger()
  private val dir = createTempDirectory()
  private val time = TestTimeSource()

  private fun cache(
    dir: Path = this.dir,
    respond: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
  ): PosterCache {
    val engine = MockEngine { request ->
      upstream.incrementAndGet()
      respond(request)
    }
    return PosterCache(dir, PoliteFetcher(HttpClient(engine), "TimeTabler/test"), time)
  }

  @Test
  fun `concurrent requests for one poster fetch it once`(): Unit = runBlocking {
    val cache = cache {
      delay(200)
      respond(png())
    }

    val results =
      (1..4).map { async { cache.jpeg(EventId("e1"), "https://img.example/e1.png") } }.awaitAll()

    upstream.get() shouldBe 1
    results.map { it?.size }.toSet().size shouldBe 1
  }

  @Test
  fun `a changed source url fetches the poster again`(): Unit = runBlocking {
    val cache = cache { respond(png()) }
    cache.jpeg(EventId("e1"), "https://img.example/old.png")

    cache.jpeg(EventId("e1"), "https://img.example/new.png")
    cache.jpeg(EventId("e1"), "https://img.example/new.png")

    upstream.get() shouldBe 2
    dir.listDirectoryEntries("*.jpg").size shouldBe 1
  }

  @Test
  fun `a failing source is retried only after an hour`(): Unit = runBlocking {
    val cache = cache { respondError(HttpStatusCode.NotFound) }
    val id = EventId("e1")

    cache.jpeg(id, "https://img.example/gone.png").shouldBeNull()
    time += 59.minutes
    cache.jpeg(id, "https://img.example/gone.png").shouldBeNull()
    val withinTheHour = upstream.get()
    time += 2.minutes
    cache.jpeg(id, "https://img.example/gone.png")

    withinTheHour shouldBe 1
    upstream.get() shouldBe 2
  }

  @Test
  fun `a source that is not an image is retried only after an hour`(): Unit = runBlocking {
    val cache = cache { respond("<html>not an image</html>") }

    cache.jpeg(EventId("e1"), "https://img.example/page").shouldBeNull()
    cache.jpeg(EventId("e1"), "https://img.example/page").shouldBeNull()

    upstream.get() shouldBe 1
  }

  @Test
  fun `a failed poster with a new url is fetched at once`(): Unit = runBlocking {
    var fail = true
    val cache = cache { if (fail) respondError(HttpStatusCode.NotFound) else respond(png()) }
    cache.jpeg(EventId("e1"), "https://img.example/gone.png")
    fail = false

    val jpeg = cache.jpeg(EventId("e1"), "https://img.example/fixed.png")

    (jpeg != null) shouldBe true
  }

  @Test
  fun `transparent posters are drawn on the theme background`(): Unit = runBlocking {
    val cache = cache { respond(png(BufferedImage.TYPE_INT_ARGB)) }

    val jpeg = cache.jpeg(EventId("e1"), "https://img.example/clear.png")!!

    val pixel = ImageIO.read(jpeg.inputStream()).getRGB(300, 300)
    for (shift in listOf(16, 8, 0)) {
      val channel = (pixel shr shift) and 0xFF
      val expected = (THEME_BACKGROUND_RGB shr shift) and 0xFF
      abs(channel - expected) shouldBeLessThanOrEqual 3
    }
  }

  @Test
  fun `prefetch fetches each poster once`(): Unit = runBlocking {
    val cache = cache { respond(png()) }
    val posters =
      listOf(
        PosterRef(EventId("e1"), "https://img.example/e1.png"),
        PosterRef(EventId("e2"), "https://img.example/e2.png"),
      )

    val first = cache.prefetch(posters)
    val second = cache.prefetch(posters)

    first shouldBe 2
    second shouldBe 0
    upstream.get() shouldBe 2
  }

  @Test
  fun `prefetch goes on past posters it cannot store`(): Unit = runBlocking {
    val cache = cache(dir = createTempFile()) { respond(png()) }
    val posters =
      listOf(
        PosterRef(EventId("e1"), "https://img.example/e1.png"),
        PosterRef(EventId("e2"), "https://img.example/e2.png"),
      )

    val first = cache.prefetch(posters)
    val second = cache.prefetch(posters)

    first shouldBe 0
    second shouldBe 0
    upstream.get() shouldBe 2
  }
}
