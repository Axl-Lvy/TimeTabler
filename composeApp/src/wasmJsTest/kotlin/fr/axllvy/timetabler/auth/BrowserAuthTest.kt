package fr.axllvy.timetabler.auth

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class BrowserAuthTest {

  @Test
  fun `the challenge matches the RFC 7636 example`() = runTest {
    val challenge = s256("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")

    challenge shouldBe "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
  }

  @Test
  fun `every sign-in gets new secrets`() = runTest {
    val first = newSignInSecrets()

    val second = newSignInSecrets()

    second.state shouldNotBe first.state
    second.verifier shouldNotBe first.verifier
    second.challenge shouldBe s256(second.verifier)
  }

  @Test
  fun `local storage keeps a value`() {
    val store = LocalStorageStore()

    store.put("test.kept", "value")

    store.get("test.kept") shouldBe "value"
  }

  @Test
  fun `a removed value is gone from local storage`() {
    val store = LocalStorageStore()
    store.put("test.removed", "value")

    store.remove("test.removed")

    store.get("test.removed").shouldBeNull()
  }
}
