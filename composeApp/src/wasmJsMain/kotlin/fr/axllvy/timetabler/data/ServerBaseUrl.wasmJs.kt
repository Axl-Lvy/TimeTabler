@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.axllvy.timetabler.data

import kotlin.js.ExperimentalWasmJsInterop

private fun pageOrigin(): String = js("window.location.origin")

// The web app is served by the TimeTabler server itself. Absolute, because the image loader
// ignores relative URLs.
actual fun serverBaseUrl(): String = pageOrigin()
