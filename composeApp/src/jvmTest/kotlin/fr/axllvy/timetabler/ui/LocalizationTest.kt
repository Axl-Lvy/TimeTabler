package fr.axllvy.timetabler.ui

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.util.Locale
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getStringArray
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import timetabler.composeapp.generated.resources.Res
import timetabler.composeapp.generated.resources.day_names_short
import timetabler.composeapp.generated.resources.event_tickets_on
import timetabler.composeapp.generated.resources.events_filter_tonight
import timetabler.composeapp.generated.resources.month_names_short
import timetabler.composeapp.generated.resources.night_tonight

class LocalizationTest {

  /** The resource environment of a desktop set to [locale]. */
  private fun environment(locale: Locale): ResourceEnvironment {
    val default = Locale.getDefault()
    Locale.setDefault(locale)
    try {
      return getSystemResourceEnvironment()
    } finally {
      Locale.setDefault(default)
    }
  }

  private val french = environment(Locale.FRENCH)
  private val english = environment(Locale.ENGLISH)

  private suspend fun dateNames(environment: ResourceEnvironment) =
    DateNames(
      getStringArray(environment, Res.array.day_names_short),
      getStringArray(environment, Res.array.month_names_short),
    )

  @Test
  fun `looks up french strings`() = runTest {
    val tonight = getString(french, Res.string.events_filter_tonight)
    val tickets = getString(french, Res.string.event_tickets_on, "DICE")

    tonight shouldBe "Ce soir"
    tickets shouldBe "Billets sur DICE"
  }

  @Test
  fun `falls back to english for other languages`() = runTest {
    val tonight = getString(environment(Locale.GERMAN), Res.string.events_filter_tonight)

    tonight shouldBe "Tonight"
  }

  @Test
  fun `formats dates in french`() = runTest {
    val names = dateNames(french)

    val friday = shortDate(LocalDate(2026, 10, 30), names)
    val sunday = shortDate(LocalDate(2027, 2, 14), names)
    val tonight = getString(french, Res.string.night_tonight, friday)

    friday shouldBe "ven. 30 oct."
    sunday shouldBe "dim. 14 févr."
    tonight shouldBe "Ce soir · ven. 30 oct."
  }

  @Test
  fun `formats dates in english`() = runTest {
    val names = dateNames(english)

    val friday = shortDate(LocalDate(2026, 10, 30), names)
    val saturday = shortDate(LocalDate(2026, 10, 31), names)
    val tonight = getString(english, Res.string.night_tonight, friday)

    tonight shouldBe "Tonight · Fri 30 Oct"
    saturday shouldBe "Sat 31 Oct"
  }

  @Test
  fun `names every day and month in both languages`() = runTest {
    val names = listOf(dateNames(english), dateNames(french))

    names.forEach {
      it.days shouldHaveSize 7
      it.months shouldHaveSize 12
    }
  }
}
