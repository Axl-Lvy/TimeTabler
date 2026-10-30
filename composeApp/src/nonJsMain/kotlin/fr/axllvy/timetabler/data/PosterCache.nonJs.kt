package fr.axllvy.timetabler.data

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun posterCache(context: PlatformContext): PosterCache =
  CoilPosterCache(SingletonImageLoader.get(context), context)

/** [PosterCache] on the disk cache of [loader], which keys network images by their URL. */
class CoilPosterCache(private val loader: ImageLoader, private val context: PlatformContext) :
  PosterCache {

  override suspend fun contains(url: String): Boolean {
    val diskCache = loader.diskCache ?: return false
    return withContext(Dispatchers.IO) { diskCache.openSnapshot(url)?.use { true } ?: false }
  }

  override suspend fun download(url: String) {
    val request =
      ImageRequest.Builder(context)
        .data(url)
        // Only the disk copy is wanted: a small decode that leaves shown posters in memory.
        .memoryCachePolicy(CachePolicy.DISABLED)
        .size(Size(1, 1))
        .build()
    val result = loader.execute(request)
    if (result is ErrorResult) throw result.throwable
  }
}
