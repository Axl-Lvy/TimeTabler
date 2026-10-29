package fr.axllvy.timetabler.server.posters

import fr.axllvy.timetabler.domain.EventId
import fr.axllvy.timetabler.server.http.PoliteFetcher
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

private const val MAX_WIDTH = 600
private const val JPEG_QUALITY = 0.85f

/** Disk cache of event posters, each fetched once from its source and stored as a small JPEG. */
class PosterCache(private val dir: Path, private val fetcher: PoliteFetcher) {

  /**
   * The cached JPEG of [id], fetching and resizing [sourceUrl] on first use; null when it cannot be
   * read.
   */
  suspend fun jpeg(id: EventId, sourceUrl: String): ByteArray? {
    val file = dir.resolve("${id.value}.jpg")
    if (file.exists()) return file.readBytes()
    val source = fetcher.getBytes(sourceUrl) ?: return null
    val image = runCatching { ImageIO.read(source.inputStream()) }.getOrNull() ?: return null
    val jpeg = encode(resize(image))
    dir.createDirectories()
    file.writeBytes(jpeg)
    return jpeg
  }

  private fun resize(image: BufferedImage): BufferedImage {
    val width = minOf(image.width, MAX_WIDTH)
    val height = image.height * width / image.width
    return BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { target ->
      val g = target.createGraphics()
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
