package fr.axllvy.timetabler.auth

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class AdminApiTest {

  private val auth =
    AdminAuth(
      oidc = oidcClient { tokens("access-1", "refresh-1", idToken("""{"sub":"owner"}""")) },
      store = MapStore(),
      clientId = "spa",
      redirectUri = "https://app.test/admin/callback",
      newSecrets = { error("no sign-in expected") },
      navigate = {},
      currentPath = { "/" },
    )

  private fun api(status: HttpStatusCode, body: String = ""): Pair<AdminApi, List<String?>> {
    val authorizations = mutableListOf<String?>()
    val engine = MockEngine { request ->
      authorizations += request.headers[HttpHeaders.Authorization]
      respond(body, status)
    }
    return AdminApi(HttpClient(engine), "https://app.test", auth) to authorizations
  }

  @Test
  fun `an admin is confirmed with the signed-in token`() = runTest {
    auth.completeSignIn(CodeGrant("code", "verifier"))
    val (api, authorizations) = api(HttpStatusCode.OK, """{"subject":"owner"}""")

    val check = api.check()

    check shouldBe AdminCheck.Admin("owner")
    authorizations shouldBe listOf("Bearer access-1")
  }

  @Test
  fun `an account without the admin role is not an admin`() = runTest {
    auth.completeSignIn(CodeGrant("code", "verifier"))
    val (api, _) = api(HttpStatusCode.Forbidden)

    val check = api.check()

    check shouldBe AdminCheck.NotAdmin
  }

  @Test
  fun `nothing is sent while signed out`() = runTest {
    val (api, authorizations) = api(HttpStatusCode.OK, """{"subject":"owner"}""")

    val check = api.check()

    check shouldBe AdminCheck.SignedOut
    authorizations shouldBe emptyList()
  }

  @Test
  fun `a server that cannot be reached is reported`() = runTest {
    auth.completeSignIn(CodeGrant("code", "verifier"))
    val api = AdminApi(HttpClient(MockEngine { throw IllegalStateException("offline") }), "", auth)

    val check = api.check()

    check shouldBe AdminCheck.Unreachable
  }
}
