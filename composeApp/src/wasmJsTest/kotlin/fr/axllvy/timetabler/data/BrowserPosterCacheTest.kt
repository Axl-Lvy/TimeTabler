@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.axllvy.timetabler.data

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest

private fun putPoster(cacheName: String, url: String): Promise<JsAny?> =
  js("caches.open(cacheName).then((cache) => cache.put(url, new Response('poster')))")

private fun deleteCache(cacheName: String): Promise<JsAny?> = js("caches.delete(cacheName)")

class BrowserPosterCacheTest {

  private val cache = BrowserPosterCache()

  @AfterTest fun deletePosters() = runTest { deleteCache(POSTER_CACHE_NAME).await<JsAny?>() }

  @Test
  fun `a poster is cached at its hash only`() = runTest {
    val url = "/v1/posters/a?v=1"
    putPoster(POSTER_CACHE_NAME, url).await<JsAny?>()

    val atItsHash = cache.contains(url)
    val atAnother = cache.contains("/v1/posters/a?v=2")

    atItsHash shouldBe true
    atAnother shouldBe false
  }

  @Test
  fun `a poster the server cannot serve throws and stays uncached`() = runTest {
    val url = "/v1/posters/missing?v=1"

    shouldThrowAny { cache.download(url) }

    cache.contains(url) shouldBe false
  }
}
