package fr.axllvy.timetabler

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import fr.axllvy.timetabler.data.EventSync
import fr.axllvy.timetabler.di.appModule
import fr.axllvy.timetabler.di.platformModule
import fr.axllvy.timetabler.domain.EventId
import fr.axllvy.timetabler.event.EventScreen
import fr.axllvy.timetabler.list.EventsScreen
import fr.axllvy.timetabler.ui.TimeTablerTheme
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import org.koin.compose.KoinIsolatedContext
import org.koin.compose.koinInject
import org.koin.core.module.Module
import org.koin.dsl.koinApplication

private val SYNC_INTERVAL = 5.minutes

/** Root composable of the TimeTabler app, wired with [modules]. */
@Composable
fun App(modules: List<Module> = listOf(platformModule(), appModule)) {
  setSingletonImageLoaderFactory { context ->
    ImageLoader.Builder(context).components { add(KtorNetworkFetcherFactory()) }.build()
  }
  val koin = remember(modules) { koinApplication { modules(modules) } }
  KoinIsolatedContext(context = koin) {
    val sync = koinInject<EventSync>()
    LaunchedEffect(sync) {
      while (true) {
        sync.syncOnce()
        delay(SYNC_INTERVAL)
      }
    }
    TimeTablerTheme {
      Surface(Modifier.fillMaxSize()) {
        val navController = rememberNavController()
        NavHost(navController, startDestination = "events") {
          composable("events") {
            EventsScreen(onOpen = { navController.navigate("event/${it.value}") })
          }
          composable("event/{id}") { entry ->
            EventScreen(
              EventId(entry.savedStateHandle.get<String>("id").orEmpty()),
              onBack = { navController.popBackStack() },
            )
          }
        }
      }
    }
  }
}
