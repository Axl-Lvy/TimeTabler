package fr.axllvy.timetabler.data

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Client settings every platform uses: a request on a dead network gives up after [requestTimeout].
 */
fun HttpClientConfig<*>.timeTablerDefaults(requestTimeout: Duration = 15.seconds) {
  install(HttpTimeout) {
    requestTimeoutMillis = requestTimeout.inWholeMilliseconds
    connectTimeoutMillis = minOf(requestTimeout, 10.seconds).inWholeMilliseconds
  }
}
