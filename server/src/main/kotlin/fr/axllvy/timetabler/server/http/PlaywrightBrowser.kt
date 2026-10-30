package fr.axllvy.timetabler.server.http

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.PlaywrightException
import com.microsoft.playwright.options.WaitUntilState
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// Not needed to read a page; skipping them spares the host and the browser's memory.
private val SKIPPED_RESOURCES = setOf("image", "media", "font")

// Playwright reports a driver error as "Error {", then "  message='<what failed>" on the next line.
private val DRIVER_MESSAGE = Regex("""message='([^\n]*)""")

/**
 * Headless Chromium driven by Playwright. A session launches the browser, loads one page at a time
 * at least [minInterval] apart, and closes the browser when it ends.
 */
class PlaywrightBrowser(
  private val userAgentSuffix: String,
  private val minInterval: Duration = 2.seconds,
  private val timeout: Duration = 60.seconds,
) : PageBrowser {

  override suspend fun <T> session(block: suspend (PageLoader) -> T): T {
    // Playwright objects may only be used from the thread that created them.
    val thread =
      Executors.newSingleThreadExecutor { Thread(it, "playwright").apply { isDaemon = true } }
        .asCoroutineDispatcher()
    try {
      // Not cancellable, or a cancellation during the launch would orphan the driver process.
      val playwright = withContext(NonCancellable + thread) { Playwright.create() }
      try {
        val page = withContext(thread) { openPage(playwright) }
        var last: TimeMark? = null
        return block { url ->
          last?.let { delay(minInterval - it.elapsedNow()) }
          try {
            withContext(thread) { load(page, url) }
          } finally {
            last = TimeSource.Monotonic.markNow()
          }
        }
      } finally {
        withContext(NonCancellable + thread) { playwright.close() }
      }
    } finally {
      thread.close()
    }
  }

  private fun openPage(playwright: Playwright): Page {
    val browser =
      playwright
        .chromium()
        .launch(BrowserType.LaunchOptions().setArgs(listOf("--disable-dev-shm-usage")))
    val context = browser.newContext(Browser.NewContextOptions().setUserAgent(userAgent(browser)))
    context.setDefaultNavigationTimeout(timeout.inWholeMilliseconds.toDouble())
    return context.newPage().apply {
      route("**/*") { route ->
        if (route.request().resourceType() in SKIPPED_RESOURCES) route.abort() else route.resume()
      }
    }
  }

  // Chromium's own user agent, so the page renders as in a browser, followed by who is asking.
  private fun userAgent(browser: Browser): String =
    browser.newPage().use { "${it.evaluate("navigator.userAgent")} $userAgentSuffix" }

  private fun load(page: Page, url: String): FetchResult =
    try {
      val response =
        page.navigate(url, Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED))
      when {
        response == null -> FetchResult.Failed(null, "no navigation response")
        response.ok() -> FetchResult.Ok(page.content())
        else -> FetchResult.Failed(response.status(), response.statusText())
      }
    } catch (e: PlaywrightException) {
      FetchResult.Failed(null, reason(e))
    }

  private fun reason(e: PlaywrightException): String {
    val text = e.message.orEmpty()
    return DRIVER_MESSAGE.find(text)?.groupValues?.get(1)?.removeSuffix("'")
      ?: text.lineSequence().firstOrNull { it.isNotBlank() }
      ?: "browser error"
  }
}
