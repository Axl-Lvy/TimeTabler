package fr.axllvy.timetabler.list

import fr.axllvy.timetabler.domain.nightEnd
import fr.axllvy.timetabler.domain.tonight
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate

/** The night in progress on [clock], emitted again within a minute of each new night starting. */
fun tonightUpdates(clock: Clock = Clock.System): Flow<LocalDate> = flow {
  while (true) {
    val now = clock.now()
    val night = tonight(now)
    emit(night)
    // Capped at a minute: delays run on a clock that stops while the device sleeps.
    delay((nightEnd(night) - now).coerceIn(1.seconds, 1.minutes))
  }
}
  .distinctUntilChanged()
