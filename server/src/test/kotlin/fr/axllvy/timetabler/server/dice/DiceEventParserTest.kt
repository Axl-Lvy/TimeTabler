package fr.axllvy.timetabler.server.dice

import fr.axllvy.timetabler.server.fixture
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class DiceEventParserTest {

  @Test
  fun `reads linked and free text artists in order`() {
    DiceEventParser.performers(fixture("dice/event-with-lineup.html")) shouldBe
      listOf("Baron Von Trax", "Cattoni", "Gray", "MaaxD", "VolviK")
  }

  @Test
  fun `an event without artists has an empty lineup`() {
    DiceEventParser.performers(fixture("dice/event-without-lineup.html")) shouldBe emptyList()
  }

  @Test
  fun `falls back to JSON-LD performers without page data`() {
    val html =
      fixture("dice/event-with-lineup.html")
        .replace(Regex("""<script id="__NEXT_DATA__"[^>]*>.*?</script>""", RegexOption.DOT_MATCHES_ALL), "")

    DiceEventParser.performers(html) shouldBe listOf("Baron Von Trax")
  }

  @Test
  fun `a page without event data gives null`() {
    DiceEventParser.performers(fixture("dice/event-without-data.html")).shouldBeNull()
  }
}
