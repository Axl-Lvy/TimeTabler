@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.axllvy.timetabler.domain

import kotlin.js.ExperimentalWasmJsInterop

@JsModule("@js-joda/timezone") private external object JsJodaTimeZoneModule

private val jsJodaTimeZones = JsJodaTimeZoneModule

internal actual fun loadTimeZoneDatabase() {
  jsJodaTimeZones
}
