package fr.axllvy.timetabler.server.posters

import fr.axllvy.timetabler.domain.EventId
import fr.axllvy.timetabler.server.events.PosterRef
import fr.axllvy.timetabler.server.http.PoliteFetcher
import fr.axllvy.timetabler.wire.THEME_BACKGROUND_RGB
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val MAX_WIDTH = 600
private const val JPEG_QUALITY = 0.85f
private const val VERSION_LENGTH = 12
private val FAILURE_TTL = 1.hours

/** Short hash of a poster's source URL, which changes whenever the source URL does. */
fun posterVersion(sourceUrl: String): String =
  MessageDigest.getInstance("SHA-256")
    .digest(sourceUrl.toByteArray())
    .joinToString("") { "%02x".format(it) }
    .take(VERSION_LENGTH)

/**
 * Disk cache of event posters, each source URL fetched once and stored as a small JPEG on the theme
 * background. A source that fails is not fetched again for an hour.
 */
class PosterCache(
  private val dir: Path,
  private val fetcher: PoliteFetcher,
  private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {

  private val inFlight = ConcurrentHashMap<EventId, Mutex>()
  private val failedAt = ConcurrentHashMap<String, ComparableTimeMark>()

  /**
   * The cached JPEG of [id] for [sourceUrl], fetching and resizing it on first use; null when it
   * cannot be read. Concurrent calls for one id fetch it once.
   */
  suspend fun jpeg(id: EventId, sourceUrl: String): ByteArray? {
    val file = fileOf(id, sourceUrl)
    readIfCached(file)?.let {
      return it
    }
    return inFlight
      .computeIfAbsent(id) { Mutex() }
      .withLock {
        readIfCached(file)?.let {
          return@withLock it
        }
        if (failedAt[file.name]?.let { it.elapsedNow() < FAILURE_TTL } == true) {
          return@withLock null
        }
        val jpeg =
          fetcher.getBytes(sourceUrl)?.let { source ->
            withContext(Dispatchers.IO) { convertAndStore(id, source, file) }
          }
        if (jpeg == null) failedAt[file.name] = timeSource.markNow() else failedAt.remove(file.name)
        jpeg
      }
  }

  /** Fetches each of [posters] not cached yet; returns how many it fetched. */
  suspend fun prefetch(posters: Collection<PosterRef>): Int = posters.count { ref ->
    val cached = withContext(Dispatchers.IO) { fileOf(ref.id, ref.url).exists() }
    !cached && jpeg(ref.id, ref.url) != null
  }

  private fun fileOf(id: EventId, sourceUrl: String): Path =
    dir.resolve("${id.value}-${posterVersion(sourceUrl)}.jpg")

  private suspend fun readIfCached(file: Path): ByteArray? =
    withContext(Dispatchers.IO) { if (file.exists()) file.readBytes() else null }

  // Null when the source is not an image ImageIO can read.
  private fun convertAndStore(id: EventId, source: ByteArray, file: Path): ByteArray? {
    val image = runCatching { ImageIO.read(source.inputStream()) }.getOrNull() ?: return null
    val jpeg = encode(resize(image))
    dir.createDirectories()
    // Written aside then moved, so a reader never sees a half-written poster.
    val partial = file.resolveSibling("${file.name}.part")
    partial.writeBytes(jpeg)
    partial.moveTo(file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    val stale = Regex("""${Regex.escape(id.value)}(-[0-9a-f]{$VERSION_LENGTH})?\.jpg""")
    dir
      .listDirectoryEntries("${id.value}*.jpg")
      .filter { it != file && stale.matches(it.name) }
      .forEach { it.deleteIfExists() }
    return jpeg
  }

  private fun resize(image: BufferedImage): BufferedImage {
    val width = minOf(image.width, MAX_WIDTH)
    val height = image.height * width / image.width
    return BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { target ->
      val g = target.createGraphics()
      g.color = Color(THEME_BACKGROUND_RGB)
      g.fillRect(0, 0, width, height)
      g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION,
        RenderingHints.VALUE_INTERPOLATION_BILINEAR,
      )
      g.drawImage(image, 0, 0, width, height, null)
      g.dispose()
    }
  }

  private fun encode(image: BufferedImage): ByteArray {
    val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
    val params =
      writer.defaultWriteParam.apply {
        compressionMode = ImageWriteParam.MODE_EXPLICIT
        compressionQuality = JPEG_QUALITY
      }
    val out = ByteArrayOutputStream()
    ImageIO.createImageOutputStream(out).use { stream ->
      writer.output = stream
      writer.write(null, IIOImage(image, null, null), params)
    }
    writer.dispose()
    return out.toByteArray()
  }
}
