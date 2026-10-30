@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.axllvy.timetabler.auth

import kotlin.io.encoding.Base64
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set

private const val SECRET_BYTES = 32

/** Fresh sign-in secrets from the browser's cryptographic random source, with an S256 challenge. */
suspend fun newSignInSecrets(): SignInSecrets {
  val verifier = BASE64_URL.encode(randomBytes(SECRET_BYTES))
  return SignInSecrets(BASE64_URL.encode(randomBytes(SECRET_BYTES)), verifier, s256(verifier))
}

/** The PKCE S256 challenge of [verifier]. */
internal suspend fun s256(verifier: String): String =
  BASE64_URL.encode(sha256(verifier.encodeToByteArray()))

/** The browser's local storage of this origin. */
class LocalStorageStore : KeyValueStore {
  override fun get(key: String): String? = localStorageGet(key)

  override fun put(key: String, value: String) = localStorageSet(key, value)

  override fun remove(key: String) = localStorageRemove(key)
}

private val BASE64_URL = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

private fun randomBytes(size: Int): ByteArray {
  val buffer = Uint8Array(size)
  getRandomValues(buffer)
  return ByteArray(size) { buffer[it] }
}

private suspend fun sha256(bytes: ByteArray): ByteArray {
  val input = Uint8Array(bytes.size)
  bytes.forEachIndexed { i, byte -> input[i] = byte }
  val digest = Int8Array(digestSha256(input).await())
  return ByteArray(digest.length) { digest[it] }
}

private fun getRandomValues(buffer: Uint8Array): Unit = js("{ crypto.getRandomValues(buffer); }")

private fun digestSha256(data: Uint8Array): Promise<ArrayBuffer> =
  js("crypto.subtle.digest('SHA-256', data)")

private fun localStorageGet(key: String): String? = js("window.localStorage.getItem(key)")

private fun localStorageSet(key: String, value: String): Unit =
  js("{ window.localStorage.setItem(key, value); }")

private fun localStorageRemove(key: String): Unit = js("{ window.localStorage.removeItem(key); }")
