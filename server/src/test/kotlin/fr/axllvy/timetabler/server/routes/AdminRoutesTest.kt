package fr.axllvy.timetabler.server.routes

import fr.axllvy.timetabler.server.PostgresTestDb
import fr.axllvy.timetabler.server.auth.AuthConfig
import fr.axllvy.timetabler.server.auth.TestIssuer
import fr.axllvy.timetabler.server.events.EventStore
import fr.axllvy.timetabler.server.health.ScrapeRuns
import fr.axllvy.timetabler.server.offlineFetcher
import fr.axllvy.timetabler.server.posters.PosterCache
import fr.axllvy.timetabler.server.timeTablerModule
import fr.axllvy.timetabler.wire.AdminIdentity
import fr.axllvy.timetabler.wire.WIRE_JSON
import io.kotest.matchers.shouldBe
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class AdminRoutesTest {

  private val db = PostgresTestDb.freshDataSource()
  private val store = EventStore(db)
  private val issuer = TestIssuer()

  @AfterTest
  fun close() {
    issuer.close()
    db.close()
  }

  private fun app(
    auth: AuthConfig? = issuer.config,
    test: suspend ApplicationTestBuilder.() -> Unit,
  ) = testApplication {
    application {
      timeTablerModule(
        store,
        PosterCache(createTempDirectory(), offlineFetcher()),
        ScrapeRuns(store),
        staticDir = null,
        auth = auth,
      )
    }
    test()
  }

  private suspend fun ApplicationTestBuilder.me(token: String? = null): HttpResponse =
    client.get("/v1/admin/me") { token?.let { bearerAuth(it) } }

  @Test
  fun `a request without a token is unauthorized`() = app {
    val response = me()

    response.status shouldBe HttpStatusCode.Unauthorized
  }

  @Test
  fun `a token without the admin scope is forbidden`() = app {
    val response = me(issuer.token(scope = "openid profile"))

    response.status shouldBe HttpStatusCode.Forbidden
  }

  @Test
  fun `a token without any scope is forbidden`() = app {
    val response = me(issuer.token(scope = null))

    response.status shouldBe HttpStatusCode.Forbidden
  }

  @Test
  fun `an admin token answers its subject`() = app {
    val response = me(issuer.token(subject = "owner-sub", scope = "openid admin"))

    response.status shouldBe HttpStatusCode.OK
    WIRE_JSON.decodeFromString(AdminIdentity.serializer(), response.bodyAsText()) shouldBe
      AdminIdentity("owner-sub")
  }

  @Test
  fun `a token for another audience is unauthorized`() = app {
    val response = me(issuer.token(audience = "https://other.test/api"))

    response.status shouldBe HttpStatusCode.Unauthorized
  }

  @Test
  fun `an expired token is unauthorized`() = app {
    val response = me(issuer.token(expiresAt = Clock.System.now() - 1.minutes))

    response.status shouldBe HttpStatusCode.Unauthorized
  }

  @Test
  fun `a token signed by another key is unauthorized`() = app {
    val response = me(issuer.forgedToken())

    response.status shouldBe HttpStatusCode.Unauthorized
  }

  @Test
  fun `without auth configuration even an admin token is unauthorized`() =
    app(auth = null) {
      val response = me(issuer.token())

      response.status shouldBe HttpStatusCode.Unauthorized
    }

  @Test
  fun `reading routes need no token without auth configuration`() =
    app(auth = null) {
      val response = client.get("/v1/events")

      response.status shouldBe HttpStatusCode.OK
    }
}
