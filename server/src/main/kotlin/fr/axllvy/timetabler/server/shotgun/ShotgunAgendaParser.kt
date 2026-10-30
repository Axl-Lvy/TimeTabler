package fr.axllvy.timetabler.server.shotgun

import org.jsoup.Jsoup

/** Shotgun's Paris agenda; `page=30` lists the whole city on one page. */
const val SHOTGUN_AGENDA_URL = "https://shotgun.live/fr/cities/paris?page=30"

private val EVENT_PATH = Regex("""^(?:https://shotgun\.live)?/fr/events/([^/?#]+)""")

/** Reads the events listed on a Shotgun agenda or venue page. */
object ShotgunAgendaParser {

  /** Slugs of the events the page links to, in page order, each once. */
  fun eventSlugs(html: String): List<String> =
    Jsoup.parse(html)
      .select("a[href]")
      .mapNotNull { EVENT_PATH.find(it.attr("href"))?.groupValues?.get(1) }
      .distinct()

  /** URL of the event page with [slug]. */
  fun eventUrl(slug: String): String = "https://shotgun.live/fr/events/$slug"
}
