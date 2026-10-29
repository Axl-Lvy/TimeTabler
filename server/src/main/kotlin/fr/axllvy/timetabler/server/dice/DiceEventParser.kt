package fr.axllvy.timetabler.server.dice

import kotlinx.serialization.json.JsonArray
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Reads the lineup of a DICE event page. */
object DiceEventParser {

  /** Artist names in billing order, or null when the page carries no event data at all. */
  fun performers(html: String): List<String>? {
    val document = Jsoup.parse(html)
    return fromPageData(document) ?: fromJsonLd(document)
  }

  private fun fromPageData(document: Document): List<String>? {
    val state = document.nextData()["props"]["pageProps"]["initialState"].string ?: return null
    val event =
      runCatching { LENIENT_JSON.parseToJsonElement(state) }.getOrNull()["event"] ?: return null
    val lineup = event["lineup"] as? JsonArray ?: return null
    return lineup.mapNotNull { entry ->
      when (entry["type"].string) {
          "artist" -> entry["name"].string
          "section_header" -> entry["title"].string
          else -> null
        }
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    }
  }

  private fun fromJsonLd(document: Document): List<String>? =
    document
      .select("script[type=application/ld+json]")
      .mapNotNull { runCatching { LENIENT_JSON.parseToJsonElement(it.data()) }.getOrNull() }
      .firstOrNull { it["@type"].string == "MusicEvent" }
      ?.let { event ->
        (event["performer"] as? JsonArray)?.mapNotNull { it["name"].string?.trim() } ?: emptyList()
      }
}
