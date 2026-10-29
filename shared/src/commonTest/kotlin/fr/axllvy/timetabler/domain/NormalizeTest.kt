package fr.axllvy.timetabler.domain

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class NormalizeTest {

  @Test
  fun `strips accents case and punctuation`() {
    normalizeText("  Clara CUVÉ / b2b  Héctor-Oaks! ") shouldBe "clara cuve b2b hector oaks"
  }

  @Test
  fun `folds ligatures and cedillas`() {
    normalizeText("Garçon Œuvre Ñu") shouldBe "garcon oeuvre nu"
  }
}
