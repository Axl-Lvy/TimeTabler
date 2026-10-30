package fr.axllvy.timetabler.auth

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Scopes requested at sign-in: `admin` is only granted to accounts holding the Logto `admin` role,
 * and `offline_access` yields a refresh token.
 */
private const val SCOPES = "openid offline_access profile admin"

/** Speaks OAuth 2.0 with PKCE and OpenID Connect with [issuer], for access tokens to [audience]. */
class OidcClient(
  private val httpClient: HttpClient,
  private val issuer: String,
  private val audience: String,
) {

  /** URL of the issuer's sign-in page, which redirects to [redirectUri] with a code. */
  fun authorizationUrl(
    clientId: String,
    redirectUri: String,
    codeChallenge: String,
    state: String,
  ): String =
    URLBuilder("$issuer/auth")
      .apply {
        parameters.append("response_type", "code")
        parameters.append("client_id", clientId)
        parameters.append("redirect_uri", redirectUri)
        parameters.append("code_challenge_method", "S256")
        parameters.append("code_challenge", codeChallenge)
        parameters.append("scope", SCOPES)
        parameters.append("resource", audience)
        parameters.append("prompt", "consent")
        parameters.append("state", state)
      }
      .buildString()

  /** Exchanges the [code] the redirect carried for a token set. */
  suspend fun exchangeCode(
    clientId: String,
    redirectUri: String,
    code: String,
    codeVerifier: String,
  ): TokenExchange =
    tokenRequest(
      Parameters.build {
        append("grant_type", "authorization_code")
        append("code", code)
        append("redirect_uri", redirectUri)
        append("client_id", clientId)
        append("code_verifier", codeVerifier)
        append("resource", audience)
      }
    )

  /**
   * Exchanges [refreshToken] for a new token set. The issuer rotates refresh tokens, so the caller
   * keeps the returned one.
   */
  suspend fun refresh(clientId: String, refreshToken: RefreshToken): TokenExchange =
    tokenRequest(
      Parameters.build {
        append("grant_type", "refresh_token")
        append("refresh_token", refreshToken.value)
        append("client_id", clientId)
        append("resource", audience)
      }
    )

  private suspend fun tokenRequest(parameters: Parameters): TokenExchange =
    try {
      val response = httpClient.submitForm("$issuer/token", parameters)
      when {
        response.status.isSuccess() -> {
          val payload = JSON.decodeFromString(TokenResponse.serializer(), response.bodyAsText())
          TokenExchange.Ok(
            accessToken = AccessToken(payload.accessToken),
            refreshToken = payload.refreshToken?.let(::RefreshToken),
            expiresIn = payload.expiresIn.seconds,
            account = payload.idToken?.let(::decodeIdToken),
          )
        }
        response.status == HttpStatusCode.BadRequest -> {
          LOG.w { "Token request rejected with ${response.status}" }
          TokenExchange.Rejected
        }
        else -> TokenExchange.Failed("Token request answered ${response.status}")
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      // Throwable, not Exception: a failed browser fetch on wasm is a JsException.
      LOG.w { "Token request failed: ${e.message}" }
      TokenExchange.Failed(e.message ?: "Token request failed")
    }
}

/** Outcome of [OidcClient.exchangeCode] and [OidcClient.refresh]. */
sealed interface TokenExchange {

  /** A token set; [account] is decoded from the id token when the issuer sent one. */
  data class Ok(
    val accessToken: AccessToken,
    val refreshToken: RefreshToken?,
    val expiresIn: Duration,
    val account: AdminAccount?,
  ) : TokenExchange

  /** The issuer refused the grant itself (HTTP 400), such as an expired or reused code. */
  data object Rejected : TokenExchange

  /** Any other failure: another status, the network or an unreadable answer. */
  data class Failed(val message: String) : TokenExchange
}

/** A signed-in account: [sub] identifies it, [name] is for display only. */
data class AdminAccount(val sub: String, val name: String?)

@Serializable
private data class TokenResponse(
  @SerialName("access_token") val accessToken: String,
  @SerialName("refresh_token") val refreshToken: String? = null,
  @SerialName("expires_in") val expiresIn: Long,
  @SerialName("id_token") val idToken: String? = null,
)

@Serializable
private data class IdTokenClaims(
  val sub: String,
  val name: String? = null,
  val username: String? = null,
)

/**
 * The account in an id token, unverified: it came over TLS straight from the issuer's token
 * endpoint, and it is only shown, never trusted. Null when the token cannot be read.
 */
internal fun decodeIdToken(idToken: String): AdminAccount? {
  val payload = idToken.split(".").takeIf { it.size == 3 }?.get(1) ?: return null
  return try {
    val json = BASE64_URL.decode(payload).decodeToString()
    val claims = JSON.decodeFromString(IdTokenClaims.serializer(), json)
    AdminAccount(claims.sub, claims.name ?: claims.username)
  } catch (e: Exception) {
    LOG.w(e) { "Could not read the id token" }
    null
  }
}

private val BASE64_URL = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

private val JSON = Json { ignoreUnknownKeys = true }

private val LOG = Logger.withTag("OidcClient")
