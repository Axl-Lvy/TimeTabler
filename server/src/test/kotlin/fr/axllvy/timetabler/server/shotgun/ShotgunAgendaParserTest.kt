package fr.axllvy.timetabler.server.shotgun

import fr.axllvy.timetabler.server.fixture
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldStartWith
import kotlin.test.Test

class ShotgunAgendaParserTest {

  @Test
  fun `reads each linked event once in page order`() {
    val slugs = ShotgunAgendaParser.eventSlugs(fixture("shotgun/venue-page.html"))

    slugs shouldHaveSize 25
    slugs shouldStartWith
      listOf(
        "tresor-paris-miamao",
        "orka-sopik-nightowl-djratz-keros-skollaris-mia-mao",
        "analog-echoes-planetary-assault-systems-fjaak-mia-mao",
      )
  }

  @Test
  fun `ignores artist venue and city links`() {
    val html =
      """<a href="/fr/artists/sopik">Sopik</a><a href="/fr/venues/mia-mao">Mia Mao</a>
        <a href="/fr/cities/paris">Paris</a><a href="https://shotgun.live/fr/events/orka">Orka</a>"""

    ShotgunAgendaParser.eventSlugs(html) shouldContainExactly listOf("orka")
  }

  @Test
  fun `a bot checkpoint has no events`() {
    ShotgunAgendaParser.eventSlugs(fixture("shotgun/checkpoint.html")).shouldBeEmpty()
  }
}
