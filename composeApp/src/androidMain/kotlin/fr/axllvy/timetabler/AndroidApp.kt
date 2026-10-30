package fr.axllvy.timetabler

import androidx.compose.runtime.Composable
import fr.axllvy.timetabler.di.appModule
import fr.axllvy.timetabler.di.platformModule
import org.koin.dsl.koinApplication

// Lives as long as the process, so a recreated activity reuses its database and HTTP client.
private val processKoin by lazy { koinApplication { modules(platformModule(), appModule) } }

/** Root composable of the Android app, sharing one Koin application across activities. */
@Composable
fun AndroidApp() {
  App(processKoin)
}
