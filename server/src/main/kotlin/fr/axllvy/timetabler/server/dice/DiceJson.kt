package fr.axllvy.timetabler.server.dice

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.nodes.Document

internal val LENIENT_JSON = Json { ignoreUnknownKeys = true }

/** The `__NEXT_DATA__` payload of a Next.js page, or null when the page has none. */
internal fun Document.nextData(): JsonObject? =
  selectFirst("script#__NEXT_DATA__")?.data()?.let {
    runCatching { LENIENT_JSON.parseToJsonElement(it) as? JsonObject }.getOrNull()
  }

internal operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)

internal val JsonElement?.string: String?
  get() = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
