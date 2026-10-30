package fr.axllvy.timetabler.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.sun.net.httpserver.HttpServer
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.URI
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/** An OIDC issuer for tests: signs tokens with a fresh RSA key and serves its JWKS locally. */
class TestIssuer : AutoCloseable {

  private val keys = newKeyPair()
  private val publicKey = keys.public as RSAPublicKey
  private val algorithm = Algorithm.RSA256(publicKey, keys.private as RSAPrivateKey)

  private val server =
    HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
      createContext("/oidc/jwks") { exchange ->
        val body = jwks().toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
      }
      start()
    }

  /** Issuer URL tokens are signed for. */
  val issuer = "http://localhost:${server.address.port}/oidc"

  /** Server configuration trusting this issuer for [AUDIENCE]. */
  val config = AuthConfig(issuer, AUDIENCE, URI("$issuer/jwks"))

  /** A signed access token for [subject] with the space-separated [scope], if any. */
  fun token(
    subject: String = "owner",
    scope: String? = ADMIN_SCOPE,
    audience: String = AUDIENCE,
    expiresAt: Instant = Clock.System.now() + 10.minutes,
  ): String = sign(subject, scope, audience, expiresAt, algorithm)

  /** An admin token like [token] but signed by another key under the same key id. */
  fun forgedToken(): String {
    val other = newKeyPair()
    val forger = Algorithm.RSA256(other.public as RSAPublicKey, other.private as RSAPrivateKey)
    return sign("owner", ADMIN_SCOPE, AUDIENCE, Clock.System.now() + 10.minutes, forger)
  }

  private fun sign(
    subject: String,
    scope: String?,
    audience: String,
    expiresAt: Instant,
    signer: Algorithm,
  ): String =
    JWT.create()
      .withKeyId(KEY_ID)
      .withIssuer(issuer)
      .withAudience(audience)
      .withSubject(subject)
      .withExpiresAt(expiresAt.toJavaInstant())
      .apply { scope?.let { withClaim("scope", it) } }
      .sign(signer)

  private fun jwks(): String =
    """{"keys":[{"kty":"RSA","kid":"$KEY_ID","use":"sig","alg":"RS256",""" +
      """"n":"${publicKey.modulus.base64Url()}","e":"${publicKey.publicExponent.base64Url()}"}]}"""

  override fun close() {
    server.stop(0)
  }

  companion object {
    /** Audience of the API resource the test tokens are for. */
    const val AUDIENCE = "https://timetabler.test/api"
    private const val KEY_ID = "test-key"
  }
}

private fun newKeyPair() =
  KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

/** Unpadded base64url of the unsigned big-endian bytes, as JWKS encodes RSA numbers. */
private fun BigInteger.base64Url(): String =
  Base64.getUrlEncoder()
    .withoutPadding()
    .encodeToString(toByteArray().dropWhile { it == 0.toByte() }.toByteArray())
