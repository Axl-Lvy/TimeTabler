@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.axllvy.timetabler

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.bindToBrowserNavigation
import androidx.savedstate.read
import fr.axllvy.timetabler.domain.EventId
import fr.axllvy.timetabler.nav.EVENT_ROUTE
import fr.axllvy.timetabler.nav.eventIdOfWebPath
import fr.axllvy.timetabler.nav.eventRoute
import fr.axllvy.timetabler.nav.eventWebPath
import kotlin.js.ExperimentalWasmJsInterop

private fun pagePath(): String = js("window.location.pathname")

private fun showAppRoot(): Unit = js("window.history.replaceState(window.history.state, '', '/')")

/** Entry point of the web app. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  val startEvent = eventIdOfWebPath(pagePath())
  ComposeViewport { App(onNavHostReady = { it.bindToHistory(startEvent) }) }
}

/** Opens [startEvent], if any, then keeps the browser history and URL in step with this app. */
@OptIn(ExperimentalBrowserHistoryApi::class)
private suspend fun NavController.bindToHistory(startEvent: EventId?) {
  startEvent?.let { navigate(eventRoute(it)) }
  // The binding prefixes every page path with the one current when it starts.
  showAppRoot()
  bindToBrowserNavigation { entry -> entry.webPath().removePrefix("/") }
}

private fun NavBackStackEntry.webPath(): String {
  val id = arguments?.read { getStringOrNull("id") }
  return if (destination.route == EVENT_ROUTE && id != null) eventWebPath(EventId(id)) else "/"
}
