package fr.axllvy.timetabler.auth

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class AdminAuthTest {

  private val store = MapStore()
  private var now = Instant.parse("2026-11-07T22:00:00Z")
  private val opened = mutableListOf<String>()
  private val tokenRequests = mutableListOf<String?>()

  private fun auth(
    issuer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
      error("no request expected")
    }
  ) =
    AdminAuth(
      oidc =
        oidcClient { request ->
          tokenRequests += (request.body as FormDataContent).formData["grant_type"]
          issuer(request)
        },
      store = store,
      clientId = "spa",
      redirectUri = "https://app.test/admin/callback",
      newSecrets = { SignInSecrets("state-1", "verifier-1", "challenge-1") },
      navigate = { opened += it },
      currentPath = { "/event/e1" },
      now = { now },
    )

  @Test
  fun `signing in opens the issuer page with this attempt's state`() = runTest {
    auth().signIn()

    Url(opened.single()).parameters["state"] shouldBe "state-1"
    Url(opened.single()).parameters["code_challenge"] shouldBe "challenge-1"
  }

  @Test
  fun `the callback of the pending sign-in returns its code and page`() = runTest {
    val auth = auth()
    auth.signIn()

    val callback = auth.takeCallback("?code=the-code&state=state-1")

    callback shouldBe SignInCallback("/event/e1", CodeGrant("the-code", "verifier-1"))
  }

  @Test
  fun `a callback with another state gives no code`() = runTest {
    val auth = auth()
    auth.signIn()

    val callback = auth.takeCallback("?code=the-code&state=forged")

    callback.grant.shouldBeNull()
  }

  @Test
  fun `a callback is only taken once`() = runTest {
    val auth = auth()
    auth.signIn()
    auth.takeCallback("?code=the-code&state=state-1")

    val replay = auth.takeCallback("?code=the-code&state=state-1")

    replay shouldBe SignInCallback("/", null)
  }

  @Test
  fun `a callback carrying an error gives no code`() = runTest {
    val auth = auth()
    auth.signIn()

    val callback = auth.takeCallback("?error=access_denied&state=state-1")

    callback.grant.shouldBeNull()
  }

  @Test
  fun `completing the sign-in stores the session`() = runTest {
    val auth = auth { tokens("access-1", "refresh-1", idToken("""{"sub":"owner","name":"Axel"}""")) }

    val signedIn = auth.completeSignIn(CodeGrant("the-code", "verifier-1"))

    signedIn shouldBe true
    auth.account.value shouldBe AdminAccount("owner", "Axel")
    auth.accessToken() shouldBe AccessToken("access-1")
  }

  @Test
  fun `the session survives a reload`() = runTest {
    auth { tokens("access-1", "refresh-1", idToken("""{"sub":"owner"}""")) }
      .completeSignIn(CodeGrant("the-code", "verifier-1"))

    val reloaded = auth()

    reloaded.account.value shouldBe AdminAccount("owner", null)
    reloaded.accessToken() shouldBe AccessToken("access-1")
  }

  @Test
  fun `a token about to expire is refreshed`() = runTest {
    var issued = 0
    val auth = auth {
      issued++
      tokens("access-$issued", "refresh-$issued", idToken("""{"sub":"owner"}"""))
    }
    auth.completeSignIn(CodeGrant("the-code", "verifier-1"))
    now += 60.minutes - 30.seconds

    val token = auth.accessToken()

    token shouldBe AccessToken("access-2")
    tokenRequests shouldBe listOf("authorization_code", "refresh_token")
  }

  @Test
  fun `a refused refresh signs out`() = runTest {
    var refused = false
    val auth = auth {
      if (refused) respond("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest)
      else tokens("access-1", "refresh-1", idToken("""{"sub":"owner"}"""))
    }
    auth.completeSignIn(CodeGrant("the-code", "verifier-1"))
    refused = true
    now += 2.minutes + 60.minutes

    val token = auth.accessToken()

    token.shouldBeNull()
    auth.account.value.shouldBeNull()
  }

  @Test
  fun `a refresh that cannot reach the issuer keeps the session`() = runTest {
    var offline = false
    val auth = auth {
      if (offline) respond("", HttpStatusCode.BadGateway)
      else tokens("access-1", "refresh-1", idToken("""{"sub":"owner"}"""))
    }
    auth.completeSignIn(CodeGrant("the-code", "verifier-1"))
    offline = true
    now += 2.minutes + 60.minutes

    val token = auth.accessToken()

    token.shouldBeNull()
    auth.account.value shouldBe AdminAccount("owner", null)
  }

  @Test
  fun `signing out forgets the session`() = runTest {
    val auth = auth { tokens("access-1", "refresh-1", idToken("""{"sub":"owner"}""")) }
    auth.completeSignIn(CodeGrant("the-code", "verifier-1"))

    auth.signOut()

    auth.account.value.shouldBeNull()
    auth.accessToken().shouldBeNull()
    store.values shouldBe emptyMap()
  }
}
