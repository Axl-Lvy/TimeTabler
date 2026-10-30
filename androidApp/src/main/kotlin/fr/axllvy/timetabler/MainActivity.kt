package fr.axllvy.timetabler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/** Entry point of the Android app. */
class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AndroidContext.initialize(this)
    setContent { AndroidApp() }
  }
}
