package fr.axllvy.timetabler

import android.content.Context

/** Application context, set once by the Android activity before anything reads it. */
object AndroidContext {
  lateinit var context: Context
    private set

  /** Records the application context of [context]. */
  fun initialize(context: Context) {
    this.context = context.applicationContext
  }
}
