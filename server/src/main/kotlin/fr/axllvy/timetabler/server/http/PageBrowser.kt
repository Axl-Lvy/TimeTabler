package fr.axllvy.timetabler.server.http

/** Loads pages one at a time in an open browser session. */
fun interface PageLoader {
  /** Loads [url] and returns the rendered page, or why there is none. */
  suspend fun load(url: String): FetchResult
}

/** A browser opened for one scrape run. */
interface PageBrowser {
  /** Opens the browser, runs [block] with its page loader, and closes the browser afterwards. */
  suspend fun <T> session(block: suspend (PageLoader) -> T): T
}
