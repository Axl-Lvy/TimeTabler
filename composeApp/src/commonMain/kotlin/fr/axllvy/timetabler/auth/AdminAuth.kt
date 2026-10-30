package fr.axllvy.timetabler.auth

import io.ktor.http.parseQueryString
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Small string settings that survive a reload of the app, such as the browser's local storage. */
interface KeyValueStore {
  /** The value under [key], or null when there is none. */
  fun get(key: String): String?

  /** Stores [value] under [key], replacing any previous one. */
  fun put(key: String, value: String)

  /** Removes the value under [key], if any. */
  fun remove(key: String)
}

/** One-time secrets of a sign-in attempt: the CSRF [state] and the PKCE verifier and challenge. */
data class SignInSecrets(val state: String, val verifier: String, val challenge: String)

/** What the redirect back from the issuer resolved to; [grant] is null when it cannot sign in. */
data class SignInCallback(val returnPath: String, val grant: CodeGrant?)

/** An authorization code with the PKCE verifier of the attempt that asked for it. */
data class CodeGrant(val code: String, val verifier: String)

/**
 * The admin session: signs in through a full-page redirect to the issuer, keeps the tokens in
 * [store] and refreshes the access token before it expires.
 */
class AdminAuth(
  private val oidc: OidcClient,
  private val store: KeyValueStore,
  private val clientId: String,
  private val redirectUri: String,
  private val newSecrets: suspend () -> SignInSecrets,
  private val navigate: (String) -> Unit,
  private val currentPath: () -> String,
  private val now: () -> Instant = { Clock.System.now() },
) {

  private val refreshLock = Mutex()
  private val _account = MutableStateFlow(storedAccount())

  /** The signed-in account, or null when signed out. */
  val account: StateFlow<AdminAccount?> = _account.asStateFlow()

  /** Leaves the app for the issuer's sign-in page; the current page reopens once it completes. */
  suspend fun signIn() {
    val secrets = newSecrets()
    store.put(PENDING_STATE, secrets.state)
    store.put(PENDING_VERIFIER, secrets.verifier)
    store.put(PENDING_RETURN_PATH, currentPath())
    navigate(oidc.authorizationUrl(clientId, redirectUri, secrets.challenge, secrets.state))
  }

  /**
   * Consumes the pending sign-in against the [query] of the redirect back from the issuer. The
   * grant is only returned when the redirect carries a code and the state of that sign-in.
   */
  fun takeCallback(query: String): SignInCallback {
    val params = parseQueryString(query.removePrefix("?"))
    val state = store.get(PENDING_STATE)
    val verifier = store.get(PENDING_VERIFIER)
    val returnPath = store.get(PENDING_RETURN_PATH) ?: "/"
    listOf(PENDING_STATE, PENDING_VERIFIER, PENDING_RETURN_PATH).forEach(store::remove)
    val code = params["code"]
    val grant =
      if (code != null && verifier != null && state != null && params["state"] == state) {
        CodeGrant(code, verifier)
      } else {
        null
      }
    return SignInCallback(returnPath, grant)
  }

  /** Exchanges [grant] for tokens and stores them; false when the issuer refused or failed. */
  suspend fun completeSignIn(grant: CodeGrant): Boolean =
    when (val result = oidc.exchangeCode(clientId, redirectUri, grant.code, grant.verifier)) {
      is TokenExchange.Ok -> {
        save(result)
        true
      }
      else -> false
    }

  /** Forgets the session on this device; the issuer is not called. */
  fun signOut() {
    listOf(ACCESS_TOKEN, REFRESH_TOKEN, EXPIRES_AT, SUB, NAME).forEach(store::remove)
    _account.value = null
  }

  /**
   * A valid access token, refreshed first when it is about to expire. Null when signed out or when
   * the refresh failed; a refresh the issuer refuses also signs out.
   */
  suspend fun accessToken(): AccessToken? {
    freshToken()?.let {
      return it
    }
    return refreshLock.withLock {
      freshToken()?.let {
        return@withLock it
      }
      val refreshToken = store.get(REFRESH_TOKEN)?.let(::RefreshToken)
      if (refreshToken == null) {
        signOut()
        return@withLock null
      }
      when (val result = oidc.refresh(clientId, refreshToken)) {
        is TokenExchange.Ok -> {
          save(result)
          result.accessToken
        }
        TokenExchange.Rejected -> {
          signOut()
          null
        }
        is TokenExchange.Failed -> null
      }
    }
  }

  private fun freshToken(): AccessToken? {
    val token = store.get(ACCESS_TOKEN) ?: return null
    val expiresAt = store.get(EXPIRES_AT)?.toLongOrNull() ?: return null
    val remaining = Instant.fromEpochMilliseconds(expiresAt) - now()
    return if (remaining > REFRESH_MARGIN) AccessToken(token) else null
  }

  private fun save(tokens: TokenExchange.Ok) {
    store.put(ACCESS_TOKEN, tokens.accessToken.value)
    tokens.refreshToken?.let { store.put(REFRESH_TOKEN, it.value) } ?: store.remove(REFRESH_TOKEN)
    val expiresAt = now() + tokens.expiresInSeconds.seconds
    store.put(EXPIRES_AT, expiresAt.toEpochMilliseconds().toString())
    tokens.account?.let { account ->
      store.put(SUB, account.sub)
      account.name?.let { store.put(NAME, it) } ?: store.remove(NAME)
      _account.value = account
    }
  }

  private fun storedAccount(): AdminAccount? =
    store.get(SUB)?.let { AdminAccount(it, store.get(NAME)) }

  private companion object {
    val REFRESH_MARGIN = 60.seconds
    const val ACCESS_TOKEN = "admin.accessToken"
    const val REFRESH_TOKEN = "admin.refreshToken"
    const val EXPIRES_AT = "admin.expiresAtMillis"
    const val SUB = "admin.sub"
    const val NAME = "admin.name"
    const val PENDING_STATE = "admin.pending.state"
    const val PENDING_VERIFIER = "admin.pending.verifier"
    const val PENDING_RETURN_PATH = "admin.pending.returnPath"
  }
}
