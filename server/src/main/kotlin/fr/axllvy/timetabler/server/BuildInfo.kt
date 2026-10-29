package fr.axllvy.timetabler.server

import java.util.Properties

/** Short git sha this server was built from, or "dev" outside the Docker build. */
val BUILD_SHA: String by lazy {
  Properties()
    .apply { object {}.javaClass.getResourceAsStream("/build-info.properties")?.use { load(it) } }
    .getProperty("buildSha", "dev")
}
