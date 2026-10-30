package fr.axllvy.timetabler.data

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath

class CoilPosterCacheTest {

  private val directory = Files.createTempDirectory("posters")
  private val requested = mutableListOf<String>()
  private val engine = MockEngine { request ->
    requested += request.url.toString()
    if (request.url.encodedPath == "/v1/posters/a") {
      respond(png(), headers = headersOf(HttpHeaders.ContentType, "image/png"))
    } else {
      respondError(HttpStatusCode.NotFound)
    }
  }
  private val loader =
    ImageLoader.Builder(PlatformContext.INSTANCE)
      .components { add(KtorNetworkFetcherFactory(HttpClient(engine))) }
      .diskCache { DiskCache.Builder().directory(directory.toOkioPath()).build() }
      .build()
  private val cache = CoilPosterCache(loader, PlatformContext.INSTANCE)

  @AfterTest
  fun deleteDirectory() {
    loader.shutdown()
    directory.toFile().deleteRecursively()
  }

  private fun png(): ByteArray {
    val bytes = ByteArrayOutputStream()
    ImageIO.write(BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", bytes)
    return bytes.toByteArray()
  }

  @Test
  fun `a downloaded poster is cached at its hash only`() = runTest {
    val url = "https://tt.test/v1/posters/a?v=1"

    val before = cache.contains(url)
    cache.download(url)

    before shouldBe false
    cache.contains(url) shouldBe true
    cache.contains("https://tt.test/v1/posters/a?v=2") shouldBe false
    requested shouldBe listOf(url)
  }

  @Test
  fun `a poster the server cannot serve throws and stays uncached`() = runTest {
    val url = "https://tt.test/v1/posters/missing?v=1"

    shouldThrowAny { cache.download(url) }

    cache.contains(url) shouldBe false
  }
}
