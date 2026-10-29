package fr.axllvy.timetabler.di

import fr.axllvy.timetabler.data.EventStoreLocal
import fr.axllvy.timetabler.data.IndexedDbEventStore
import fr.axllvy.timetabler.data.timeTablerDefaults
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
  single { HttpClient { timeTablerDefaults() } }
  single<EventStoreLocal> { IndexedDbEventStore() }
}
