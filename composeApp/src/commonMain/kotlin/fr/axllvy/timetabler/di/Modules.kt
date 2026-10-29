package fr.axllvy.timetabler.di

import fr.axllvy.timetabler.data.EventSync
import fr.axllvy.timetabler.data.EventsApi
import fr.axllvy.timetabler.data.serverBaseUrl
import org.koin.core.module.Module
import org.koin.dsl.module

/** Platform bindings: the HTTP client and the local event store. */
expect fun platformModule(): Module

/** Bindings shared by every platform. */
val appModule = module {
  single { EventsApi(get(), serverBaseUrl()) }
  single { EventSync(get(), get()) }
}
