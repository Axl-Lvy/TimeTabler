package fr.axllvy.timetabler.data

import fr.axllvy.timetabler.domain.Revision
import fr.axllvy.timetabler.wire.EventsPage
import fr.axllvy.timetabler.wire.WIRE_JSON
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/** Client of the server's event feed at [baseUrl]. */
class EventsApi(private val client: HttpClient, private val baseUrl: String) {

  /**
   * The feed page after [since].
   *
   * @throws IllegalStateException when the server answers with an error status.
   */
  suspend fun page(since: Revision, limit: Int = 500): EventsPage {
    val response =
      client.get("$baseUrl/v1/events") {
        parameter("since", since.value)
        parameter("limit", limit)
      }
    check(response.status.isSuccess()) { "Event feed answered ${response.status}" }
    return WIRE_JSON.decodeFromString(EventsPage.serializer(), response.bodyAsText())
  }
}

/** Base URL of the TimeTabler server for this platform, without a trailing slash. */
expect fun serverBaseUrl(): String
