package fr.axllvy.timetabler

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

/** Entry point of the web app. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  ComposeViewport { App() }
}
