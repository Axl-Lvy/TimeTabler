package fr.axllvy.timetabler.server.routes

import io.ktor.http.CacheControl
import io.ktor.server.http.content.staticFiles
import io.ktor.server.response.cacheControl
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.io.File

private const val HASHED_ASSET_MAX_AGE_SECONDS = 31_536_000

/**
 * Content-hashed entry files of the wasm build (`composeApp.<hash>.js`, `styles.<hash>.css`); must
 * keep matching the names `hashWasmJsBundle` in `composeApp/build.gradle.kts` produces.
 */
private val HASHED_ASSET_PATTERN = Regex("""(composeApp|styles)\.[0-9a-f]{20}\.(js|css)""")

/**
 * Client-side pages of the web app, answered with its shell so a reload, a shared link or the
 * sign-in redirect works; must keep matching `eventWebPath` and `ADMIN_CALLBACK_PATH` in
 * `composeApp`. Event ids are lowercase UUIDs, so bundle files resolved under `/event/` stay a 404
 * instead of the shell.
 */
private val APP_PAGES =
  listOf(
    Regex("""/event/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"""),
    Regex("""/admin/callback"""),
  )

/**
 * Serves the web bundle at `/` and its shell at every app page: hashed files are cached for a year,
 * everything else revalidated.
 */
fun Route.staticFrontendRoutes(staticDir: File) {
  for (page in APP_PAGES) {
    get(page) {
      call.response.cacheControl(CacheControl.NoCache(visibility = null))
      call.respondFile(File(staticDir, "index.html"))
    }
  }
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
