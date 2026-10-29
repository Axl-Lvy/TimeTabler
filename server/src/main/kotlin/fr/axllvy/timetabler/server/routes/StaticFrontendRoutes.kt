package fr.axllvy.timetabler.server.routes

import io.ktor.http.CacheControl
import io.ktor.server.http.content.staticFiles
import io.ktor.server.routing.Route
import java.io.File

private const val HASHED_ASSET_MAX_AGE_SECONDS = 31_536_000

/**
 * Content-hashed entry files of the wasm build (`composeApp.<hash>.js`, `styles.<hash>.css`); must
 * keep matching the names `hashWasmJsBundle` in `composeApp/build.gradle.kts` produces.
 */
private val HASHED_ASSET_PATTERN = Regex("""(composeApp|styles)\.[0-9a-f]{20}\.(js|css)""")

/** Serves the web bundle at `/`: hashed files are cached for a year, everything else revalidated. */
fun Route.staticFrontendRoutes(staticDir: File) {
  staticFiles("/", staticDir) {
    cacheControl { file ->
      if (file.extension == "wasm" || HASHED_ASSET_PATTERN.matches(file.name)) {
        listOf(CacheControl.MaxAge(maxAgeSeconds = HASHED_ASSET_MAX_AGE_SECONDS))
      } else {
        listOf(CacheControl.NoCache(visibility = null))
      }
    }
  }
}
