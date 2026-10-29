package fr.axllvy.timetabler.server

/** Reads a test resource as text. */
fun fixture(path: String): String =
  checkNotNull(object {}.javaClass.getResource("/$path")) { "missing fixture $path" }.readText()
