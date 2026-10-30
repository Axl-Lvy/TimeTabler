package fr.axllvy.timetabler

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import fr.axllvy.timetabler.data.EventStoreLocal
import fr.axllvy.timetabler.data.EventSync
import fr.axllvy.timetabler.data.PosterPrefetcher
import fr.axllvy.timetabler.data.SyncOutcome
import fr.axllvy.timetabler.data.posterCache
import fr.axllvy.timetabler.data.serverBaseUrl
import fr.axllvy.timetabler.di.appModule
import fr.axllvy.timetabler.di.platformModule
import fr.axllvy.timetabler.domain.EventId
import fr.axllvy.timetabler.event.EventScreen
import fr.axllvy.timetabler.list.EventsScreen
import fr.axllvy.timetabler.nav.EVENTS_ROUTE
import fr.axllvy.timetabler.nav.EVENT_ROUTE
import fr.axllvy.timetabler.nav.eventRoute
import fr.axllvy.timetabler.ui.TimeTablerTheme
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.KoinIsolatedContext
import org.koin.compose.koinInject
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.koinApplication

private val SYNC_INTERVAL = 5.minutes

/**
 * Root composable of the TimeTabler app, wired with [modules] once for its whole lifetime;
 * [onNavHostReady] runs once its navigation graph is set.
 */
@Composable
fun App(
  modules: List<Module> = listOf(platformModule(), appModule),
  onNavHostReady: suspend (NavController) -> Unit = {},
) {
  // Keyed on nothing: the default list is rebuilt on every call and must not rebuild Koin.
  val koin = remember { koinApplication { modules(modules) } }
  App(koin, onNavHostReady)
}

/**
 * Root composable of the TimeTabler app on [koin], which outlives it and is never closed here;
 * [onNavHostReady] runs once its navigation graph is set.
 */
@Composable
fun App(koin: KoinApplication, onNavHostReady: suspend (NavController) -> Unit = {}) {
  setSingletonImageLoaderFactory { context ->
    ImageLoader.Builder(context).components { add(KtorNetworkFetcherFactory()) }.build()
  }
  val platformContext = LocalPlatformContext.current
  val prefetcher =
    remember(platformContext) { PosterPrefetcher(posterCache(platformContext), serverBaseUrl()) }
  KoinIsolatedContext(context = koin) {
    val sync = koinInject<EventSync>()
    val store = koinInject<EventStoreLocal>()
    LaunchedEffect(sync, store) {
      while (true) {
        if (sync.syncOnce() == SyncOutcome.Ok) {
          launch(Dispatchers.Default) { prefetcher.prefetch(store.events().first()) }
        }
        delay(SYNC_INTERVAL)
      }
    }
    TimeTablerTheme {
      Surface(Modifier.fillMaxSize()) {
        val navController = rememberNavController()
        NavHost(navController, startDestination = EVENTS_ROUTE) {
          composable(EVENTS_ROUTE) {
            EventsScreen(onOpen = { navController.navigate(eventRoute(it)) })
          }
          composable(EVENT_ROUTE) { entry ->
            EventScreen(
              EventId(entry.savedStateHandle.get<String>("id").orEmpty()),
              onBack = { navController.popBackStack() },
            )
          }
        }
        LaunchedEffect(navController) { onNavHostReady(navController) }
      }
    }
  }
}
