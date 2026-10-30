@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.axllvy.timetabler.data

import coil3.PlatformContext
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsBoolean
import kotlin.js.Promise
import kotlinx.coroutines.await

/** Cache the service worker serves posters from; `POSTERS` in sw.js must keep this name. */
const val POSTER_CACHE_NAME = "timetabler-posters-v1"

private fun isCached(url: String): Promise<JsBoolean> =
  js("caches.match(url).then((response) => response !== undefined)")

private fun addToCache(cacheName: String, url: String): Promise<JsAny?> =
  js("caches.open(cacheName).then((cache) => cache.add(url))")

actual fun posterCache(context: PlatformContext): PosterCache = BrowserPosterCache()

/** [PosterCache] on the browser cache storage the service worker serves posters from. */
class BrowserPosterCache : PosterCache {

  override suspend fun contains(url: String): Boolean = isCached(url).await<JsBoolean>().toBoolean()

  // Added from the page, not left to the service worker, so it works before the worker controls it.
  override suspend fun download(url: String) {
    addToCache(POSTER_CACHE_NAME, url).await<JsAny?>()
  }
}
