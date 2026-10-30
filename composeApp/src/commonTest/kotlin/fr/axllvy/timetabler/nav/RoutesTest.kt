package fr.axllvy.timetabler.nav

import fr.axllvy.timetabler.domain.EventId
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class RoutesTest {

  @Test
  fun `an event page shows its event`() {
    eventIdOfWebPath("/event/abc") shouldBe EventId("abc")
    eventIdOfWebPath("/event/abc/") shouldBe EventId("abc")
  }

  @Test
  fun `other pages show no event`() {
    for (path in listOf("/", "", "/event", "/event/", "/event/a/b", "/events/abc")) {
      eventIdOfWebPath(path).shouldBeNull()
    }
  }

  @Test
  fun `an event web path leads back to its event`() {
    val id = EventId("3f6c2a5e-1b2d-4c8e-9a7f-0e1d2c3b4a59")

    val path = eventWebPath(id)

    path shouldBe "/event/3f6c2a5e-1b2d-4c8e-9a7f-0e1d2c3b4a59"
    eventIdOfWebPath(path) shouldBe id
  }
}
