package fr.axllvy.timetabler

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import fr.axllvy.timetabler.data.EventStoreLocal
import fr.axllvy.timetabler.data.EventSync
import fr.axllvy.timetabler.data.EventsApi
import fr.axllvy.timetabler.data.SyncOutcome
import fr.axllvy.timetabler.data.SyncState
import fr.axllvy.timetabler.data.room.RoomEventStore
import fr.axllvy.timetabler.data.room.inMemoryDatabase
import fr.axllvy.timetabler.domain.EventId
import fr.axllvy.timetabler.domain.Revision
import fr.axllvy.timetabler.domain.nightDateOf
import fr.axllvy.timetabler.nav.eventRoute
import fr.axllvy.timetabler.ui.DateNames
import fr.axllvy.timetabler.ui.shortDate
import fr.axllvy.timetabler.wire.EventDto
import fr.axllvy.timetabler.wire.EventsPage
import fr.axllvy.timetabler.wire.ListingDto
import fr.axllvy.timetabler.wire.ListingStatus
import fr.axllvy.timetabler.wire.Source
import fr.axllvy.timetabler.wire.VenueDto
import fr.axllvy.timetabler.wire.WIRE_JSON
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import java.io.IOException
import java.util.Locale
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.koin.dsl.koinApplication
import org.koin.dsl.module

@OptIn(ExperimentalTestApi::class)
class AppUiTest {

  private val store = RoomEventStore(inMemoryDatabase())
  private val offlineApi = EventsApi(HttpClient(MockEngine { throw IOException("offline") }), "")
  private val testModules = modulesWith(EventSync(offlineApi, store))
  private val frenchDateNames =
    DateNames(
      days = listOf("lun.", "mar.", "mer.", "jeu.", "ven.", "sam.", "dim."),
      months =
        listOf(
          "janv.",
          "févr.",
          "mars",
          "avr.",
          "mai",
          "juin",
          "juil.",
          "août",
          "sept.",
          "oct.",
          "nov.",
          "déc.",
        ),
    )

  private fun modulesWith(sync: EventSync) =
    listOf(
      module {
        single<EventStoreLocal> { store }
        single { sync }
      }
    )

  /** Feed serving [pages] in order, each once [gate] completes, then empty pages. */
  private fun apiServing(
    vararg pages: EventsPage,
    gate: CompletableDeferred<Unit> = CompletableDeferred(Unit),
  ): EventsApi {
    var served = 0
    val engine = MockEngine {
      gate.await()
      val page =
        pages.getOrElse(served++) { EventsPage(emptyList(), emptyList(), pages.last().next, false) }
      respond(
        WIRE_JSON.encodeToString(EventsPage.serializer(), page),
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
      )
    }
    return EventsApi(HttpClient(engine), "")
  }

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
  fun `ticket links name their source and status`() = runComposeUiTest {
    val listings =
      listOf(
        ListingDto(Source.SHOTGUN, "https://shotgun.live/fr/events/e1", ListingStatus.SOLD_OUT),
        ListingDto(Source.UNKNOWN, "https://example.com/e1", ListingStatus.CANCELLED),
      )
    storePage(listOf(possession().copy(listings = listings)))
    setContent { App(testModules) }

    waitUntil("Possession shown", timeoutMillis = 5_000) {
      onAllNodesWithText("Possession").fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithText("Possession").performClick()

    waitUntil("Shotgun link shown", timeoutMillis = 5_000) {
      onAllNodesWithText("Tickets on Shotgun (sold out)").fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithText("Tickets on Tickets (cancelled)").assertExists()
  }

  @Test
  fun `a search without matches says so`() = runComposeUiTest {
    storePage(listOf(possession()))
    setContent { App(testModules) }
    waitUntil("Possession shown", timeoutMillis = 5_000) {
      onAllNodesWithText("Possession").fetchSemanticsNodes().isNotEmpty()
    }

    onNode(hasSetTextAction()).performTextInput("Nobody")

    waitUntil("no match message shown", timeoutMillis = 5_000) {
      onAllNodesWithText("No events match \"Nobody\"").fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test
  fun `a synced empty feed says there are no events`() = runComposeUiTest {
    val sync =
      EventSync(apiServing(EventsPage(emptyList(), emptyList(), Revision(1), false)), store)

    setContent { App(modulesWith(sync)) }

    waitUntil("no events message shown", timeoutMillis = 5_000) {
      onAllNodesWithText("No events").fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test
  fun `the app follows a french device`() {
    val default = Locale.getDefault()
    Locale.setDefault(Locale.FRENCH)
    try {
      runComposeUiTest {
        val event = possession()
        storePage(listOf(event))
        setContent { App(testModules) }

        waitUntil("Possession shown", timeoutMillis = 5_000) {
          onAllNodesWithText("Possession").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Ce soir").assertExists()
        onNodeWithText("Ce week-end").assertExists()
        onNodeWithText(
            shortDate(event.nightDate, frenchDateNames),
            substring = true,
            ignoreCase = true,
          )
          .assertExists()
        onNodeWithText("Possession").performClick()

        waitUntil("Billets sur DICE shown", timeoutMillis = 5_000) {
          onAllNodesWithText("Billets sur DICE").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Pas encore d'horaires", substring = true).assertExists()
      }
    } finally {
      Locale.setDefault(default)
    }
  }

  private fun storeOneEvent(title: String) = runBlocking {
    val startsAt = Clock.System.now() + 10.hours
    store.apply(
      EventsPage(
        listOf(
          EventDto(
            EventId("e1"),
            Revision(1),
            title,
            nightDateOf(startsAt),
            startsAt,
            null,
            VenueDto("Rex Club", null),
            emptyList(),
            null,
            emptyList(),
          )
        ),
        emptyList(),
        Revision(1),
        hasMore = false,
      )
    )
  }

  @Test
  fun `a background sync shows no refresh indicator`() = runComposeUiTest {
    storeOneEvent("Possession")
    val hangingSync =
      EventSync(EventsApi(HttpClient(MockEngine { awaitCancellation() }), ""), store)
    setContent {
      App(
        listOf(
          module {
            single<EventStoreLocal> { store }
            single { hangingSync }
          }
        )
      )
    }

    waitUntil("Possession shown while syncing", timeoutMillis = 5_000) {
      hangingSync.state.value == SyncState.SYNCING &&
        onAllNodesWithText("Possession").fetchSemanticsNodes().isNotEmpty()
    }
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
      .assertCountEquals(0)
  }

  @Test
  fun `the app syncs through the koin application it is given`() = runComposeUiTest {
    val koin = koinApplication { modules(testModules) }

    setContent { App(koin) }

    waitUntil("given sync went offline", timeoutMillis = 5_000) {
      koin.koin.get<EventSync>().state.value == SyncState.OFFLINE
    }
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
  fun `an unknown event id shows Event not found once the sync is done`() = runComposeUiTest {
    storePage(listOf(possession()))
    val gate = CompletableDeferred<Unit>()
    val sync =
      EventSync(
        apiServing(EventsPage(emptyList(), emptyList(), Revision(1), false), gate = gate),
        store,
      )
    setContent {
      App(modulesWith(sync), onNavHostReady = { it.navigate(eventRoute(EventId("missing"))) })
    }
    waitUntil("syncing", timeoutMillis = 5_000) { sync.state.value == SyncState.SYNCING }
    waitUntil("event screen shown", timeoutMillis = 5_000) {
      onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty()
    }
    waitForIdle()
    onNodeWithText("Event not found").assertDoesNotExist()

    gate.complete(Unit)

    waitUntil("Event not found shown", timeoutMillis = 5_000) {
      onAllNodesWithText("Event not found").fetchSemanticsNodes().isNotEmpty()
    }
    sync.state.value shouldBe SyncState.IDLE
  }

  @Test
  fun `an open event removed by a sync shows Event not found`() = runComposeUiTest {
    val sync =
      EventSync(
        apiServing(
          EventsPage(listOf(possession()), emptyList(), Revision(1), hasMore = false),
          EventsPage(emptyList(), listOf(EventId("e1")), Revision(2), hasMore = false),
        ),
        store,
      )
    setContent {
      App(modulesWith(sync), onNavHostReady = { it.navigate(eventRoute(EventId("e1"))) })
    }
    waitUntil("Tickets on DICE shown", timeoutMillis = 5_000) {
      onAllNodesWithText("Tickets on DICE").fetchSemanticsNodes().isNotEmpty()
    }

    runBlocking { sync.syncOnce() } shouldBe SyncOutcome.Ok

    waitUntil("Event not found shown", timeoutMillis = 5_000) {
      onAllNodesWithText("Event not found").fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithText("Possession").assertDoesNotExist()
  }

  @Test
  fun `an event missing from the offline copy asks to connect`() = runComposeUiTest {
    storePage(listOf(possession()))
    setContent {
      App(testModules, onNavHostReady = { it.navigate(eventRoute(EventId("missing"))) })
    }

    waitUntil("Not in your offline copy shown", timeoutMillis = 5_000) {
      onAllNodesWithText(NOT_OFFLINE, substring = true).fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithText("Event not found").assertDoesNotExist()
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

private const val NOT_OFFLINE = "Not in your offline copy"
