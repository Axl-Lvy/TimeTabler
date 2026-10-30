package fr.axllvy.timetabler

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import fr.axllvy.timetabler.data.EventStoreLocal
import fr.axllvy.timetabler.data.EventSync
import fr.axllvy.timetabler.data.EventsApi
import fr.axllvy.timetabler.data.room.RoomEventStore
import fr.axllvy.timetabler.data.room.inMemoryDatabase
import fr.axllvy.timetabler.domain.EventId
import fr.axllvy.timetabler.domain.Revision
import fr.axllvy.timetabler.domain.nightDateOf
import fr.axllvy.timetabler.nav.eventRoute
import fr.axllvy.timetabler.wire.EventDto
import fr.axllvy.timetabler.wire.EventsPage
import fr.axllvy.timetabler.wire.ListingDto
import fr.axllvy.timetabler.wire.ListingStatus
import fr.axllvy.timetabler.wire.Source
import fr.axllvy.timetabler.wire.VenueDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import java.io.IOException
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module

@OptIn(ExperimentalTestApi::class)
class AppUiTest {

  private val store = RoomEventStore(inMemoryDatabase())
  private val offlineApi = EventsApi(HttpClient(MockEngine { throw IOException("offline") }), "")
  private val testModules =
    listOf(
      module {
        single<EventStoreLocal> { store }
        single { offlineApi }
        single { EventSync(get(), get()) }
      }
    )

  private fun possession(): EventDto {
    val startsAt = Clock.System.now() + 10.hours
    return EventDto(
      EventId("e1"),
      Revision(1),
      "Possession",
      nightDateOf(startsAt),
      startsAt,
      null,
      VenueDto("La Machine du Moulin Rouge", null),
      listOf("I Hate Models", "Narciss"),
      null,
      listOf(ListingDto(Source.DICE, "https://dice.fm/event/e1", ListingStatus.ON_SALE)),
    )
  }

  private fun storePage(
    events: List<EventDto>,
    removed: List<EventId> = emptyList(),
    revision: Long = 1,
  ) = runBlocking { store.apply(EventsPage(events, removed, Revision(revision), hasMore = false)) }

  @Test
  fun `opening an event shows its lineup and ticket link`() = runComposeUiTest {
    storePage(listOf(possession()))
    setContent { App(testModules) }

    waitUntil("Possession shown", timeoutMillis = 5_000) {
      onAllNodesWithText("Possession").fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithText("Possession").performClick()

    waitUntil("Tickets on DICE shown", timeoutMillis = 5_000) {
      onAllNodesWithText("Tickets on DICE").fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithText("I Hate Models").assertExists()
    onNodeWithText("Narciss").assertExists()
    onNodeWithText("No set times yet", substring = true).assertExists()
  }

  @Test
  fun `first launch offline explains that one sync is needed`() = runComposeUiTest {
    setContent { App(testModules) }

    waitUntil("Connect once to load events shown", timeoutMillis = 5_000) {
      onAllNodesWithText("Connect once to load events").fetchSemanticsNodes().isNotEmpty()
    }
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
      .assertCountEquals(0)
  }

  @Test
  fun `an unknown event id shows Event not found`() = runComposeUiTest {
    storePage(listOf(possession()))
    setContent {
      App(testModules, onNavHostReady = { it.navigate(eventRoute(EventId("missing"))) })
    }

    waitUntil("Event not found shown", timeoutMillis = 5_000) {
      onAllNodesWithText("Event not found").fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test
  fun `an open event removed by a sync shows Event not found`() = runComposeUiTest {
    storePage(listOf(possession()))
    setContent { App(testModules, onNavHostReady = { it.navigate(eventRoute(EventId("e1"))) }) }
    waitUntil("Tickets on DICE shown", timeoutMillis = 5_000) {
      onAllNodesWithText("Tickets on DICE").fetchSemanticsNodes().isNotEmpty()
    }

    storePage(emptyList(), removed = listOf(EventId("e1")), revision = 2)

    waitUntil("Event not found shown", timeoutMillis = 5_000) {
      onAllNodesWithText("Event not found").fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithText("Possession").assertDoesNotExist()
  }

  @Test
  fun `an event link opened offline before any sync asks to connect once`() = runComposeUiTest {
    setContent { App(testModules, onNavHostReady = { it.navigate(eventRoute(EventId("e1"))) }) }

    waitUntil("Connect once to load events shown", timeoutMillis = 5_000) {
      onAllNodesWithText("Connect once to load events").fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithText("Event not found").assertDoesNotExist()
  }
}
