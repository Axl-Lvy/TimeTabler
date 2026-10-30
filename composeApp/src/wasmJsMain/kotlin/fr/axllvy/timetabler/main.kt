@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.axllvy.timetabler

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.bindToBrowserNavigation
import androidx.savedstate.read
import fr.axllvy.timetabler.auth.ADMIN_CALLBACK_PATH
import fr.axllvy.timetabler.auth.AdminAuth
import fr.axllvy.timetabler.di.appModule
import fr.axllvy.timetabler.di.platformModule
import fr.axllvy.timetabler.domain.EventId
import fr.axllvy.timetabler.nav.EVENT_ROUTE
import fr.axllvy.timetabler.nav.eventIdOfWebPath
import fr.axllvy.timetabler.nav.eventRoute
import fr.axllvy.timetabler.nav.eventWebPath
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication

private fun pagePath(): String = js("window.location.pathname")

private fun pageQuery(): String = js("window.location.search")

private fun showPath(path: String): Unit =
  js("window.history.replaceState(window.history.state, '', path)")

private fun showAppRoot(): Unit = js("window.history.replaceState(window.history.state, '', '/')")

/** Entry point of the web app. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  val koin = koinApplication { modules(platformModule(), appModule) }
  if (pagePath() == ADMIN_CALLBACK_PATH) finishAdminSignIn(koin)
  val startEvent = eventIdOfWebPath(pagePath())
  ComposeViewport { App(koin, onNavHostReady = { it.bindToHistory(startEvent) }) }
}

/**
 * Takes the issuer's redirect back: the page the sign-in started from is shown again at once, and
 * the code is exchanged for tokens in the background.
 */
private fun finishAdminSignIn(koin: KoinApplication) {
  val auth = koin.koin.getOrNull<AdminAuth>()
  if (auth == null) {
    showPath("/")
    return
  }
  val callback = auth.takeCallback(pageQuery())
  showPath(callback.returnPath)
  callback.grant?.let { grant -> MainScope().launch { auth.completeSignIn(grant) } }
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
