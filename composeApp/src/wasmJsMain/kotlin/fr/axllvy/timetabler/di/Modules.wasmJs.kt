@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.axllvy.timetabler.di

import fr.axllvy.timetabler.auth.ADMIN_CALLBACK_PATH
import fr.axllvy.timetabler.auth.ADMIN_OIDC
import fr.axllvy.timetabler.auth.AdminApi
import fr.axllvy.timetabler.auth.AdminAuth
import fr.axllvy.timetabler.auth.LocalStorageStore
import fr.axllvy.timetabler.auth.OidcClient
import fr.axllvy.timetabler.auth.newSignInSecrets
import fr.axllvy.timetabler.data.EventStoreLocal
import fr.axllvy.timetabler.data.IndexedDbEventStore
import fr.axllvy.timetabler.data.serverBaseUrl
import fr.axllvy.timetabler.data.timeTablerDefaults
import io.ktor.client.HttpClient
import kotlin.js.ExperimentalWasmJsInterop
import org.koin.core.module.Module
import org.koin.dsl.module

private fun openPage(url: String): Unit = js("{ window.location.href = url; }")

private fun pagePath(): String = js("window.location.pathname")

actual fun platformModule(): Module = module {
  single { HttpClient { timeTablerDefaults() } }
  single<EventStoreLocal> { IndexedDbEventStore() }
  // Only the web app signs in, and only once the Logto tenant is filled in.
  if (ADMIN_OIDC.isConfigured) {
    single {
      AdminAuth(
        oidc = OidcClient(get(), ADMIN_OIDC.issuer, ADMIN_OIDC.audience),
        store = LocalStorageStore(),
        clientId = ADMIN_OIDC.webClientId,
        redirectUri = serverBaseUrl() + ADMIN_CALLBACK_PATH,
        newSecrets = ::newSignInSecrets,
        navigate = ::openPage,
        currentPath = ::pagePath,
      )
    }
    single { AdminApi(get(), serverBaseUrl(), get()) }
  }
}
