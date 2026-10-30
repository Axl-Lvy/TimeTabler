package fr.axllvy.timetabler.auth

import co.touchlab.kermit.Logger
import fr.axllvy.timetabler.wire.AdminIdentity
import fr.axllvy.timetabler.wire.WIRE_JSON
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.coroutines.cancellation.CancellationException

/** Client of the server's admin routes at [baseUrl], authenticated as the [auth] session. */
class AdminApi(
  private val client: HttpClient,
  private val baseUrl: String,
  private val auth: AdminAuth,
) {

  /** Whether the server accepts the signed-in account as an admin. */
  suspend fun check(): AdminCheck {
    return try {
      val response = authorized { url("$baseUrl/v1/admin/me") } ?: return AdminCheck.SignedOut
      when (response.status) {
        HttpStatusCode.OK ->
          AdminCheck.Admin(
            WIRE_JSON.decodeFromString(AdminIdentity.serializer(), response.bodyAsText()).subject
          )
        HttpStatusCode.Unauthorized -> AdminCheck.SignedOut
        HttpStatusCode.Forbidden -> AdminCheck.NotAdmin
        else -> AdminCheck.Unreachable
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      // Throwable, not Exception: a failed browser fetch on wasm is a JsException.
      LOG.w { "Admin check failed: ${e.message}" }
      AdminCheck.Unreachable
    }
  }

  /** Sends the request [build] describes with the session's token; null when there is none. */
  private suspend fun authorized(build: HttpRequestBuilder.() -> Unit): HttpResponse? {
    val token = auth.accessToken() ?: return null
    return client.request {
      build()
      bearerAuth(token.value)
    }
  }
}

/** What the server makes of the signed-in account. */
sealed interface AdminCheck {
  /** An admin, known to the server as [subject]. */
  data class Admin(val subject: String) : AdminCheck

  /** Signed in, but the account does not hold the admin role. */
  data object NotAdmin : AdminCheck

  /** No session, or one the server no longer accepts. */
  data object SignedOut : AdminCheck

  /** The server could not be reached or answered unexpectedly. */
  data object Unreachable : AdminCheck
}

private val LOG = Logger.withTag("AdminApi")
