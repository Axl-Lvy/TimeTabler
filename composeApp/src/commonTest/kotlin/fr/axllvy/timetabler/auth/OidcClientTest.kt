package fr.axllvy.timetabler.auth

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.Url
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest

class OidcClientTest {

  @Test
  fun `the sign-in page asks for the admin scope on the api resource`() {
    val client = oidcClient { error("no request expected") }

    val url = Url(client.authorizationUrl("spa", "https://app.test/admin/callback", "chal", "st"))

    url.toString().substringBefore('?') shouldBe "$ISSUER/auth"
    url.parameters["scope"] shouldBe "openid offline_access profile admin"
    url.parameters["resource"] shouldBe AUDIENCE
    url.parameters["client_id"] shouldBe "spa"
    url.parameters["redirect_uri"] shouldBe "https://app.test/admin/callback"
    url.parameters["code_challenge"] shouldBe "chal"
    url.parameters["code_challenge_method"] shouldBe "S256"
    url.parameters["state"] shouldBe "st"
  }

  @Test
  fun `a code is exchanged for tokens on the api resource`() = runTest {
    var form: Parameters? = null
    val client = oidcClient { request ->
      form = (request.body as FormDataContent).formData
      tokens("access", "refresh", idToken("""{"sub":"owner-sub","name":"Axel"}"""))
    }

    val result = client.exchangeCode("spa", "https://app.test/admin/callback", "code", "verifier")

    result shouldBe
      TokenExchange.Ok(
        AccessToken("access"),
        RefreshToken("refresh"),
        3600.seconds,
        AdminAccount("owner-sub", "Axel"),
      )
    form?.get("grant_type") shouldBe "authorization_code"
    form?.get("code_verifier") shouldBe "verifier"
    form?.get("resource") shouldBe AUDIENCE
  }

  @Test
  fun `a refused grant is rejected`() = runTest {
    val client = oidcClient { respond("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest) }

    val result = client.refresh("spa", RefreshToken("stale"))

    result shouldBe TokenExchange.Rejected
  }

  @Test
  fun `a server error is a failure`() = runTest {
    val client = oidcClient { respond("", HttpStatusCode.BadGateway) }

    val result = client.refresh("spa", RefreshToken("refresh"))

    result.shouldBeInstanceOf<TokenExchange.Failed>()
  }

  @Test
  fun `the account falls back to the username`() {
    val account = decodeIdToken(idToken("""{"sub":"owner-sub","username":"axel"}"""))

    account shouldBe AdminAccount("owner-sub", "axel")
  }
}
