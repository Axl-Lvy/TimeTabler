package fr.axllvy.timetabler.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.io.encoding.Base64

const val ISSUER = "https://tenant.logto.test/oidc"
const val AUDIENCE = "https://timetabler.test/api"

/** A [KeyValueStore] in memory. */
class MapStore(val values: MutableMap<String, String> = mutableMapOf()) : KeyValueStore {
  override fun get(key: String): String? = values[key]

  override fun put(key: String, value: String) {
    values[key] = value
  }

  override fun remove(key: String) {
    values.remove(key)
  }
}

/** An [OidcClient] whose issuer answers every request with [handler]. */
fun oidcClient(
  handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
): OidcClient = OidcClient(HttpClient(MockEngine(handler)), ISSUER, AUDIENCE)

/** A token endpoint answer with [accessToken] and, when given, a refresh and an id token. */
fun MockRequestHandleScope.tokens(
  accessToken: String,
  refreshToken: String? = null,
  idToken: String? = null,
  expiresIn: Long = 3600,
): HttpResponseData {
  val fields =
    listOfNotNull(
      "\"access_token\":\"$accessToken\"",
      "\"expires_in\":$expiresIn",
      refreshToken?.let { "\"refresh_token\":\"$it\"" },
      idToken?.let { "\"id_token\":\"$it\"" },
      "\"token_type\":\"Bearer\"",
    )
  return respond(
    fields.joinToString(",", "{", "}"),
    HttpStatusCode.OK,
    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
  )
}

/** An unsigned id token carrying [claims] as its payload. */
fun idToken(claims: String): String {
  val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
  return "${base64.encode("""{"alg":"none"}""".encodeToByteArray())}." +
    "${base64.encode(claims.encodeToByteArray())}.signature"
}
