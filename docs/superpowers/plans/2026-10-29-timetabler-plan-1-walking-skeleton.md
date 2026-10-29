# TimeTabler Plan 1: Walking Skeleton (DICE events, end to end) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A deployed TimeTabler whose web app lists every upcoming Paris event scraped from DICE as poster cards grouped by night, with search, quick filters, an event page with lineup and ticket links, and offline use after the first sync.

**Architecture:** A Ktor server scrapes DICE politely on a krontab schedule into Postgres, assigns every changed event a monotonically increasing revision, and serves a read-only `GET /v1/events?since=` page feed, cached posters and the wasmJs bundle on one origin. Clients pull the feed into Room (Android, iOS, JVM) or IndexedDB (web) and render only from the local store.

**Tech Stack:** Kotlin 2.4.21, Compose Multiplatform 1.12.2, Ktor 3.6.0 (server and client), kotlinx.serialization 1.12.0, kotlinx-datetime 0.8.0, Room 2.8.5, `com.juul.indexeddb` 0.12.0, Koin 4.2.2, Kermit 2.2.0, krontab 3.1.0, jsoup, Coil 3, Postgres 16, HikariCP 7.1.0, Kotest assertions 6.2.6, Testcontainers 1.21.4.

**Spec:** `docs/superpowers/specs/2026-10-29-timetabler-design.md`

**Later plans (not here):** plan 2 Shotgun scraping, cross-source matching, merge and split; plan 3 Logto admin login, hand-typed timetables, running order view; plan 4 screenshot extraction after spike 1 resumes.

## Global Constraints

- Package root `fr.axllvy.timetabler`; Gradle `rootProject.name = "TimeTabler"`; default branch `main`.
- Mirror MemorChess at `~/IdeaProjects/MemorChess/A` (read-only reference). Library versions are copied from its `gradle/libs.versions.toml`; drop chess, stockfish, filekit, benchmark, tracing and hotpreview entries. Add `jsoup` (latest 1.x) and Coil 3 (`io.coil-kt.coil3:coil-compose`, `coil-network-ktor3`, latest 3.x) only.
- Gradle wrapper `9.6.1`, held below `9.7.0` (MemorChess ADR 0002). JDK 21 toolchain, JVM target 21.
- Targets: `androidLibrary{}` (via `com.android.kotlin.multiplatform.library`), `jvm()`, `iosArm64()`, `iosSimulatorArm64()`, `wasmJs { browser() }`. No desktop app entry point in this plan.
- `ktfmt { googleStyle() }` in every module; versioned pre-commit hook in `.githooks/pre-commit` running `./gradlew ktfmtCheck`.
- Domain ids and constrained values are `@JvmInline value class`; times are `kotlin.time.Instant`/`Duration`; `kotlinx-datetime` only for `LocalDate`, `LocalTime`, `TimeZone`. Storage fields stay primitive, converted in mappers. Opt in to `kotlin.time.ExperimentalTime` in every module.
- KDoc: one or two lines on public declarations, saying what, not how. No history or issue references in comments.
- Tests: `kotlin.test` runner with Kotest assertions, no mocking libraries (Ktor `MockEngine`, real Postgres via Testcontainers, test fixtures). Arrange, act, assert separated by blank lines. No commas in backtick test names.
- Testcontainers tests set `systemProperty("api.version", System.getenv("DOCKER_API_VERSION") ?: "1.40")`.
- Task 1 creates the repository and all of plan 1 runs on `main` in the main checkout, since there is no history to protect; worktrees start with plan 2.
- Run Gradle through `./gradlew` only, one build at a time (the `avmvn` wrapper is Maven-only and does not apply).
- Timezone for every local date or time: `Europe/Paris`.
- DICE: plain HTTP, page data only (`__NEXT_DATA__`, JSON-LD, and Next.js page-data routes such as `/_next/data/<buildId>/...json`). Never `events-api.dice.fm`, `api.dice.fm` or any `x-api-key`; if pagination turns out to need those, stop and ask the owner.
- App copy and date formats are English in plan 1 ("Fri 30 Oct"); i18n comes later.
- Politeness: at most one request every 2 seconds per host, User-Agent `TimeTabler/<buildSha> (+https://github.com/Axl-Lvy/TimeTabler)`.
- Sync window: events with night date from yesterday to today plus 60 days.
- Server env vars: `TT_DB_URL`, `TT_DB_USER`, `TT_DB_PASSWORD`, optional `TT_PORT` (default 8080), `TT_STATIC_DIR`, `TT_POSTER_DIR` (default `/data/posters`).
- Web responses carry `Cross-Origin-Opener-Policy: same-origin` and `Cross-Origin-Embedder-Policy: credentialless`.
- Image `ghcr.io/axl-lvy/timetabler-server:latest` and `:<sha7>`; public hostname `timetabler.axl-lvy.fr`, no Cloudflare Access on it.
- PR titles in Conventional Commit form (feat, fix, docs, test, ci, refactor, perf, chore, build, ui); individual commit messages are plain sentences, not Conventional (MemorChess `.github/git-commit-instructions.md`).
- Formatting: run `./gradlew ktfmtFormat` once at the very end in a dedicated formatting-only commit, not after each task.

## Review Focus

1. **DST night.** An event starting Sunday 25 Oct 2026 at 02:30 (the hour that repeats when clocks go back) must still belong to Saturday 24 Oct's night. Test in Task 2.
2. **DICE throttles or errors mid-run.** A 429 or 5xx on page 3 of the agenda must stop the run, keep every event already stored, and archive nothing. Test in Task 7.
3. **One malformed event.** A DICE event with no JSON-LD block or broken JSON must be skipped with a warning while the rest of the page is stored. Test in Task 4.
4. **"Tonight" after midnight.** At 03:00 on Saturday, the Tonight filter still shows Friday night's events. Test in Task 11.
5. **First launch offline.** A client that has never synced and has no network shows an explanatory empty state, not an endless spinner. Test in Task 12.

---

## File Structure

```
settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml, gradle/wrapper/*
LICENSE (GPL-3.0), README.md, CLAUDE.md, .gitignore, .githooks/pre-commit, renovate.json
.github/pull_request_template.md, .github/git-commit-instructions.md, .github/workflows/{check,deploy,pr_name_check}.yml
docs/adr/0001-three-tier-ci-checks.md, docs/adr/0002-toolchain-version-holds.md
shared/src/commonMain/kotlin/fr/axllvy/timetabler/
  domain/Ids.kt            EventId, Revision
  domain/Night.kt          PARIS, nightDateOf, tonight
  domain/Normalize.kt      normalizeText
  wire/EventsWire.kt       EventDto, VenueDto, ListingDto, Source, ListingStatus, EventsPage, WIRE_JSON
server/src/main/kotlin/fr/axllvy/timetabler/server/
  Main.kt, ServerConfig.kt, BuildInfo.kt, SchedulingModule.kt
  db/Database.kt, db/Schema.kt          (+ resources/schema.sql, resources/build-info.properties)
  http/PoliteFetcher.kt
  dice/DiceBrowseParser.kt, dice/DiceEventParser.kt, dice/DiceScraper.kt
  events/ScrapedListing.kt, events/EventStore.kt
  routes/EventRoutes.kt, routes/PosterRoutes.kt, routes/StaticFrontendRoutes.kt, routes/HealthRoute.kt
  posters/PosterCache.kt
server/src/testFixtures/kotlin/fr/axllvy/timetabler/server/PostgresTestDb.kt, TestServer.kt
server/src/test/resources/dice/{browse-page-1.html, browse-page-2.html, browse-page-bad-event.html, event-with-lineup.html, event-without-jsonld.html}
composeApp/src/commonMain/kotlin/fr/axllvy/timetabler/
  App.kt, di/Modules.kt
  data/EventStoreLocal.kt (interface), data/EventsApi.kt, data/EventSync.kt
  list/EventFilters.kt, list/EventsScreen.kt, list/EventCard.kt
  event/EventScreen.kt
composeApp/src/nonJsMain/.../data/room/{TimeTablerDatabase.kt, EventEntity.kt, EventDao.kt, RoomEventStore.kt}
composeApp/src/wasmJsMain/.../data/IndexedDbEventStore.kt
composeApp/src/wasmJsMain/resources/{index.html, sw.js}
androidApp/ (thin shell), iosApp/ (Xcode project copied from MemorChess, renamed)
e2e/src/test/kotlin/fr/axllvy/timetabler/e2e/SyncE2eTest.kt
Dockerfile
```

---

### Task 1: Repository scaffold

**Files:**
- Create: everything in the top block of File Structure, plus empty-module `build.gradle.kts` for `shared`, `server`, `composeApp`, `androidApp`, `e2e`, and `iosApp/`.

**Interfaces:**
- Produces: modules `:shared`, `:server`, `:composeApp`, `:androidApp`, `:e2e`; source set `nonJsMain` (android + jvm + ios) in `:composeApp`, as in MemorChess.

- [ ] **Step 1:** `git init -b main` in `~/IdeaProjects/TimeTabler`. Keep the existing `docs/superpowers/`. `.gitignore` = MemorChess's minus its `docs/superpowers/` line, plus `.superpowers/` and `spike/`.
- [ ] **Step 2:** Copy and adapt from MemorChess: `settings.gradle.kts` (drop benchmark modules), root `build.gradle.kts`, `gradle.properties`, `gradle/wrapper/*` (9.6.1), catalog per Global Constraints, `renovate.json` (keep groups and the wrapper hold pointing at ADR 0002), `.github/pull_request_template.md` (keep only "Force running tests"), `.github/git-commit-instructions.md`, ADRs 0001 and 0002 (rewrite the context paragraphs for TimeTabler, keep the decisions). `LICENSE` = GPL-3.0 text.
- [ ] **Step 3:** Module build files modelled on MemorChess's `shared`, `server`, `composeApp`, `androidApp`, `e2e`, with namespaces `fr.axllvy.timetabler.*`, Android app id `fr.axllvy.timetabler`, wasm `outputModuleName = "timetabler"`. Copy the `hashWasmJsBundle` task from MemorChess `composeApp/build.gradle.kts:258-296`. Server `mainClass = "fr.axllvy.timetabler.server.MainKt"`, `java-test-fixtures` plugin, the `api.version` system property and `buildSha` resource expansion as in MemorChess `server/build.gradle.kts`.
- [ ] **Step 4:** `.githooks/pre-commit` runs `./gradlew ktfmtCheck --quiet --include-only=<staged .kt/.kts files, comma-separated>` (skipped when none are staged) and exits non-zero on failure; README tells contributors to run `git config core.hooksPath .githooks`. Run that command locally.
- [ ] **Step 5:** `CLAUDE.md`: stack, module map, build and test commands, the Global Constraints above in short form, and "always develop in a worktree under `.claude/worktrees/`". `README.md`: what the app is, how to run server and web locally.
- [ ] **Step 6:** Placeholder `App()` composable showing "TimeTabler" and a server `Main.kt` that starts and exits; verify:

Run: `./gradlew :shared:compileKotlinJvm :server:compileKotlin :composeApp:compileKotlinWasmJs :androidApp:assembleDebug ktfmtCheck`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit** `git add -A && git commit -m "Scaffold TimeTabler modules and conventions"`

### Task 2: Shared night rules and normalisation

**Files:**
- Create: `shared/src/commonMain/kotlin/fr/axllvy/timetabler/domain/{Ids.kt, Night.kt, Normalize.kt}`
- Test: `shared/src/commonTest/kotlin/fr/axllvy/timetabler/domain/{NightTest.kt, NormalizeTest.kt}`

**Interfaces:**
- Produces:
  - `@JvmInline @Serializable value class EventId(val value: String)`, `@JvmInline @Serializable value class Revision(val value: Long) : Comparable<Revision>` with `companion object { val ZERO = Revision(0) }`
  - `val PARIS: TimeZone`
  - `fun nightDateOf(start: Instant): LocalDate`: local date in `PARIS` of `start - 6.hours`
  - `fun tonight(now: Instant): LocalDate` = `nightDateOf(now)`
  - `fun normalizeText(text: String): String`: lowercase, accents stripped, punctuation to spaces, whitespace collapsed and trimmed

- [ ] **Step 1: Write the failing tests**

```kotlin
class NightTest {
  private fun paris(iso: String) = LocalDateTime.parse(iso).toInstant(PARIS)

  @Test fun `evening start belongs to the same date`() {
    nightDateOf(paris("2026-10-30T23:30")) shouldBe LocalDate(2026, 10, 30)
  }
  @Test fun `after midnight start belongs to the previous date`() {
    nightDateOf(paris("2026-10-31T00:30")) shouldBe LocalDate(2026, 10, 30)
    nightDateOf(paris("2026-10-31T05:59")) shouldBe LocalDate(2026, 10, 30)
  }
  @Test fun `six in the morning starts a new night`() {
    nightDateOf(paris("2026-10-31T06:00")) shouldBe LocalDate(2026, 10, 31)
  }
  @Test fun `start in the repeated DST hour stays on the saturday night`() {
    val secondTwoThirty = Instant.parse("2026-10-25T01:30:00Z") // 02:30 CET, after clocks go back
    nightDateOf(secondTwoThirty) shouldBe LocalDate(2026, 10, 24)
  }
  @Test fun `tonight at three on saturday is friday`() {
    tonight(paris("2026-10-31T03:00")) shouldBe LocalDate(2026, 10, 30)
  }
}

class NormalizeTest {
  @Test fun `strips accents case and punctuation`() {
    normalizeText("  Clara CUVÉ / b2b  Héctor-Oaks! ") shouldBe "clara cuve b2b hector oaks"
  }
}
```

- [ ] **Step 2:** Run `./gradlew :shared:jvmTest`; expected FAIL (unresolved references).
- [ ] **Step 3:** Implement the three files. Accent stripping must work on all targets: map the Latin-1 and Latin Extended-A accented letters to their base letter with a lookup table (no `java.text.Normalizer` in common code).
- [ ] **Step 4:** Run `./gradlew :shared:jvmTest`; expected PASS.
- [ ] **Step 5: Commit** `git commit -am "Add night date rules and text normalisation"`

### Task 3: Shared wire types

**Files:**
- Create: `shared/src/commonMain/kotlin/fr/axllvy/timetabler/wire/EventsWire.kt`
- Test: `shared/src/commonTest/kotlin/fr/axllvy/timetabler/wire/EventsWireTest.kt`

**Interfaces:**
- Produces (all `@Serializable`):
  - `enum class Source { DICE, SHOTGUN }`, `enum class ListingStatus { ON_SALE, SOLD_OUT, CANCELLED, UNKNOWN }`
  - `data class VenueDto(val name: String, val address: String?)`
  - `data class ListingDto(val source: Source, val url: String, val status: ListingStatus)`
  - `data class EventDto(val id: EventId, val revision: Revision, val title: String, val nightDate: LocalDate, val startsAt: Instant, val endsAt: Instant?, val venue: VenueDto, val lineup: List<String>, val posterPath: String?, val listings: List<ListingDto>)`
  - `data class EventsPage(val events: List<EventDto>, val removed: List<EventId>, val next: Revision, val hasMore: Boolean)`
  - `val WIRE_JSON: Json` with `ignoreUnknownKeys = true`, `explicitNulls = false`

- [ ] **Step 1:** Test: an `EventsPage` with one event (two listings, lineup of three names) round-trips through `WIRE_JSON` unchanged, and decoding a payload with an extra unknown field succeeds.
- [ ] **Step 2:** Run `./gradlew :shared:jvmTest`; expected FAIL.
- [ ] **Step 3:** Implement. Use the `kotlin.time.Instant` serializer the way MemorChess's `shared/.../core/sync/` wire types do.
- [ ] **Step 4:** Run `./gradlew :shared:jvmTest`; expected PASS.
- [ ] **Step 5: Commit** `git commit -am "Add event feed wire types"`

### Task 4: DICE parsers

**Files:**
- Create: `server/src/main/kotlin/fr/axllvy/timetabler/server/dice/{DiceBrowseParser.kt, DiceEventParser.kt}`, `server/src/main/kotlin/fr/axllvy/timetabler/server/events/ScrapedListing.kt`
- Test: `server/src/test/kotlin/fr/axllvy/timetabler/server/dice/{DiceBrowseParserTest.kt, DiceEventParserTest.kt}`, fixtures in `server/src/test/resources/dice/`

**Interfaces:**
- Produces:
  - `data class ScrapedListing(val source: Source, val externalId: String, val url: String, val title: String, val startsAt: Instant, val endsAt: Instant?, val venueName: String, val venueAddress: String?, val status: ListingStatus, val posterUrl: String?, val lineup: List<String>)`
  - `data class DiceBrowsePage(val listings: List<ScrapedListing>, val nextCursor: String?)`
  - `object DiceBrowseParser { fun parse(html: String): DiceBrowsePage; fun nextPageUrl(cursor: String): String }`
  - `object DiceEventParser { fun performers(html: String): List<String>? }` (null when there is no usable `MusicEvent` JSON-LD)

- [ ] **Step 1: Capture fixtures.** `curl -sL -A "Mozilla/5.0" https://dice.fm/browse/paris > browse-page-1.html` (it redirects to `/browse/paris-5b23e8a0e63cc224a4c36a2d`). Open the browse page in a browser, scroll to load page 2, and find in devtools how the page passes `nextCursor` (likely `/_next/data/<buildId>/browse/...json?cursor=...`, with `buildId` taken from `__NEXT_DATA__`, which is allowed); save that response as `browse-page-2.html`. Save `browse-page-bad-event.html` as a copy of page 1 with one event's date removed. Save one event page with performers as `event-with-lineup.html`, and a copy with the JSON-LD `<script>` removed as `event-without-jsonld.html`. If page 2 is only reachable through `events-api.dice.fm`, `api.dice.fm` or an API key, stop and ask the owner.
- [ ] **Step 2: Write the failing tests.** Values are read from the captured fixtures and written into the tests literally:
  - `parse(browse-page-1)` returns the fixture's actual event count with a non-null `nextCursor`; the first listing's `externalId`, `title`, `startsAt`, `venueName`, `url` equal the fixture's values; every listing has `source == DICE`.
  - `parse(browse-page-bad-event)` skips the dateless event and returns the others.
  - `nextPageUrl(cursor)` equals the URL observed in Step 1.
  - `performers(event-with-lineup)` equals the fixture's performer names in order; `performers(event-without-jsonld)` is null.
- [ ] **Step 3:** Run `./gradlew :server:test --tests '*Dice*'`; expected FAIL.
- [ ] **Step 4:** Implement with jsoup (select `script#__NEXT_DATA__` and `script[type=application/ld+json]`) and kotlinx.serialization `JsonElement` navigation from `props.pageProps.events` and `props.pageProps.nextCursor`. A malformed event is logged with Kermit at warn and skipped.
- [ ] **Step 5:** Run `./gradlew :server:test --tests '*Dice*'`; expected PASS.
- [ ] **Step 6: Commit** `git add -A && git commit -m "Parse DICE browse and event pages"`

### Task 5: Polite fetcher

**Files:**
- Create: `server/src/main/kotlin/fr/axllvy/timetabler/server/http/PoliteFetcher.kt`
- Test: `server/src/test/kotlin/fr/axllvy/timetabler/server/http/PoliteFetcherTest.kt`

**Interfaces:**
- Produces:
  - `class PoliteFetcher(client: HttpClient, userAgent: String, minInterval: Duration = 2.seconds, timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic)`
  - `suspend fun get(url: String): FetchResult`
  - `sealed interface FetchResult { data class Ok(val body: String) : FetchResult; data class Failed(val status: Int?, val reason: String) : FetchResult }`

- [ ] **Step 1: Write the failing tests** using `MockEngine` and `runTest`:
  - Two `get` calls to the same host: the second request is sent at least 2 s of virtual time after the first (`testScheduler.currentTime`).
  - Calls to two different hosts are not delayed by each other.
  - Every request carries the given `User-Agent`.
  - A 429 response yields `Failed(429, ...)`; a thrown IO exception yields `Failed(null, ...)`.
- [ ] **Step 2:** Run `./gradlew :server:test --tests '*PoliteFetcher*'`; expected FAIL.
- [ ] **Step 3:** Implement with a per-host `Mutex` and last-request mark; wait with `delay` so virtual time works. Follow redirects.
- [ ] **Step 4:** Run the same command; expected PASS.
- [ ] **Step 5: Commit** `git add -A && git commit -m "Add per-host rate-limited fetcher"`

### Task 6: Database, schema and event store

**Files:**
- Create: `server/src/main/resources/schema.sql`, `server/src/main/kotlin/fr/axllvy/timetabler/server/db/{Database.kt, Schema.kt}`, `server/src/main/kotlin/fr/axllvy/timetabler/server/events/EventStore.kt`, `server/src/testFixtures/kotlin/fr/axllvy/timetabler/server/PostgresTestDb.kt`
- Test: `server/src/test/kotlin/fr/axllvy/timetabler/server/events/EventStoreTest.kt`

**Interfaces:**
- Consumes: `ScrapedListing` (Task 4), `EventId`, `Revision`, `nightDateOf` (Task 2), wire types (Task 3).
- Produces:
  - `fun applySchema(dataSource: DataSource)`
  - `class EventStore(dataSource: DataSource, clock: Clock = Clock.System)`
  - `fun upsert(listing: ScrapedListing): EventId`
  - `fun page(since: Revision, limit: Int): EventsPage`
  - `fun archiveBefore(nightDate: LocalDate): Int`
  - `data class ListingRef(val source: Source, val externalId: String, val url: String)`
  - `fun listingsStartingWithin(window: Duration): List<ListingRef>`
  - `fun setLineup(source: Source, externalId: String, lineup: List<String>)`
  - `fun posterUrl(id: EventId): String?`

- [ ] **Step 1:** `schema.sql`, idempotent (`IF NOT EXISTS`):
  - `venue(id uuid pk, name text, normalized_name text unique, address text)`
  - `event(id uuid pk, revision bigint not null, night_date date, starts_at timestamptz, ends_at timestamptz, venue_id uuid fk, title text, poster_url text, lineup text[] not null default '{}', archived boolean not null default false)`
  - `listing(id uuid pk, event_id uuid fk, source text, external_id text, url text, raw_title text, starts_at timestamptz, ends_at timestamptz, status text, poster_url text, lineup text[], content_hash text, last_seen_at timestamptz, unique(source, external_id))`
  - `create sequence if not exists event_revision_seq`
  - an index on `event(revision)`

  Copy `PostgresTestDb` from MemorChess `server/src/testFixtures/.../db/`.
- [ ] **Step 2: Write the failing tests** (Testcontainers):
  - `upsert` of a new listing creates one event whose `nightDate`, `title`, venue and lineup match, and `page(Revision.ZERO, 100)` returns it.
  - `upsert` of the identical listing again does not change the event's revision.
  - `upsert` with a changed lineup bumps the revision above every earlier one.
  - Two listings at venues named `"La Machine du Moulin Rouge"` and `"la machine du moulin rouge"` share one venue row.
  - Paging: five events, `page(ZERO, 2)` then `page(next, 2)`; update the first event between calls; draining the feed sees all five, plus the updated first event again, and the last page has `hasMore == false`.
  - `archiveBefore(d)` marks older events archived and bumps their revision; the next `page` lists their ids in `removed` and not in `events`.
- [ ] **Step 3:** Run `./gradlew :server:test --tests '*EventStore*'`; expected FAIL.
- [ ] **Step 4:** Implement with plain JDBC in one transaction per `upsert`. `content_hash` is SHA-256 of the listing's fields. When it changes, take `nextval('event_revision_seq')` for the event. In plan 1 each new listing creates its own event (matching is plan 2). `posterPath` in `EventDto` is `"/v1/posters/<id>"` when `poster_url` is set.
- [ ] **Step 5:** Run `./gradlew :server:test --tests '*EventStore*'`; expected PASS.
- [ ] **Step 6: Commit** `git add -A && git commit -m "Store scraped events with revisions"`

### Task 7: DICE scraper and schedule

**Files:**
- Create: `server/src/main/kotlin/fr/axllvy/timetabler/server/dice/DiceScraper.kt`, `server/src/main/kotlin/fr/axllvy/timetabler/server/SchedulingModule.kt`
- Test: `server/src/test/kotlin/fr/axllvy/timetabler/server/dice/DiceScraperTest.kt`

**Interfaces:**
- Consumes: `PoliteFetcher` (Task 5), `DiceBrowseParser`, `DiceEventParser` (Task 4), `EventStore` (Task 6).
- Produces:
  - `class DiceScraper(fetcher: PoliteFetcher, store: EventStore, clock: Clock)`
  - `suspend fun scrapeAgenda(): ScrapeReport`: walks pages until no cursor or until listings start beyond today plus 60 days, upserts each listing, then fetches event pages for listings that are new or changed
  - `suspend fun refreshSoon(): ScrapeReport`: event pages for `store.listingsStartingWithin(48.hours)`, each result written with `store.setLineup(ref.source, ref.externalId, performers)`
  - `data class ScrapeReport(val pages: Int, val upserted: Int, val failed: String?)`
  - `fun Application.schedulingModule(scraper: DiceScraper, store: EventStore)`

- [ ] **Step 1: Write the failing tests** with `MockEngine` serving the Task 4 fixtures and a Testcontainers store:
  - `scrapeAgenda` over fixtures page 1 then page 2 (no further cursor) stores every listing, and the lineup of the event matching `event-with-lineup.html` equals its performers.
  - A 429 on page 2: page 1's listings are stored, `failed` names the status, and no event is archived.
  - An event page without JSON-LD leaves that event's lineup empty and the run continues.
- [ ] **Step 2:** Run `./gradlew :server:test --tests '*DiceScraper*'`; expected FAIL.
- [ ] **Step 3:** Implement. Event pages are fetched only when the listing's content hash changed or it has no lineup yet.
- [ ] **Step 4:** Run `./gradlew :server:test --tests '*DiceScraper*'`; expected PASS.
- [ ] **Step 5:** In `schedulingModule`, add krontab jobs (seconds-first, UTC, as in MemorChess `SchedulingModule.kt`):
  - `scrapeAgenda` at 03:00 UTC daily, followed by `archiveBefore(yesterday)`.
  - `refreshSoon` every 3 hours.
  - One `scrapeAgenda` at boot when the event table is empty.

  Log each `ScrapeReport` with Kermit.
- [ ] **Step 6: Commit** `git add -A && git commit -m "Scrape DICE agenda on a schedule"`

### Task 8: Server application, feed and poster routes

**Files:**
- Create: `server/src/main/kotlin/fr/axllvy/timetabler/server/{Main.kt, ServerConfig.kt, BuildInfo.kt}`, `routes/{EventRoutes.kt, PosterRoutes.kt, HealthRoute.kt, StaticFrontendRoutes.kt}`, `posters/PosterCache.kt`, `server/src/testFixtures/.../TestServer.kt`
- Test: `server/src/test/kotlin/fr/axllvy/timetabler/server/routes/{EventRoutesTest.kt, PosterRoutesTest.kt, StaticFrontendRoutesTest.kt}`

**Interfaces:**
- Consumes: `EventStore` (Task 6), `PoliteFetcher` (Task 5), `WIRE_JSON` (Task 3).
- Produces:
  - `GET /v1/events?since=<long>&limit=<int>` returns an `EventsPage`; `limit` defaults to 500 and is clamped to 1..2000; a bad `since` gives 400.
  - `GET /v1/posters/{id}` returns `image/jpeg`; it gives 404 when the event is unknown or has no poster.
  - `GET /health` returns 200.
  - `/` serves the static bundle.
  - `class PosterCache(dir: Path, fetcher: PoliteFetcher)` with `suspend fun jpeg(id: EventId, sourceUrl: String): ByteArray?`.
  - `TestServer` fixture starts the app on a random port against `PostgresTestDb` (used by Task 13).

- [ ] **Step 1: Write the failing tests** (`testApplication`):
  - The feed returns the stored events as `EventsPage` JSON.
  - `since=abc` gives 400.
  - Plain unit test on the limit parser (copied from MemorChess `PageLimit.kt`): `"5000"` gives 2000, `"0"` gives 1, absent gives 500, `"abc"` is rejected.
  - Poster: the first request fetches from a `MockEngine` PNG and returns JPEG bytes no wider than 600 px. A second request makes no HTTP call. An unknown id gives 404.
  - Static: `index.html` has `Cache-Control: no-cache`, a `composeApp.<20 hex>.js` file has `max-age=31536000`, and every response carries the COOP and COEP headers from Global Constraints.
- [ ] **Step 2:** Run `./gradlew :server:test --tests '*Routes*'`; expected FAIL.
- [ ] **Step 3:** Implement. Copy `StaticFrontendRoutes` from MemorChess minus the OAuth callback parts, and add the COOP/COEP headers. Use `PageLimit` and `BuildInfo` from MemorChess. Resize posters with `javax.imageio` to a maximum width of 600 px, JPEG quality 0.85, stored as `<dir>/<id>.jpg`. `Main.kt` reads `ServerConfig` from env, applies the schema, and installs ContentNegotiation with `WIRE_JSON`, routes and `schedulingModule`.
- [ ] **Step 4:** Run `./gradlew :server:test`; expected PASS.
- [ ] **Step 5: Commit** `git add -A && git commit -m "Serve event feed, posters and web bundle"`

### Task 9: Client local store

**Files:**
- Create: `composeApp/src/commonMain/kotlin/fr/axllvy/timetabler/data/EventStoreLocal.kt`, `composeApp/src/nonJsMain/kotlin/fr/axllvy/timetabler/data/room/{TimeTablerDatabase.kt, EventEntity.kt, EventDao.kt, RoomEventStore.kt}`, `composeApp/src/wasmJsMain/kotlin/fr/axllvy/timetabler/data/IndexedDbEventStore.kt`
- Test: `composeApp/src/nonJsTest/kotlin/fr/axllvy/timetabler/data/RoomEventStoreTest.kt`, `composeApp/src/wasmJsTest/kotlin/fr/axllvy/timetabler/data/IndexedDbEventStoreTest.kt`

**Interfaces:**
- Consumes: `EventDto`, `EventsPage`, `Revision`, `EventId` (Tasks 2 and 3).
- Produces:
  - `interface EventStoreLocal`
  - `fun events(): Flow<List<EventDto>>`
  - `fun event(id: EventId): Flow<EventDto?>`
  - `suspend fun apply(page: EventsPage)`: upserts events, deletes `removed`, and stores `page.next` as the last revision in the same transaction
  - `suspend fun lastRevision(): Revision`
  - `suspend fun hasEverSynced(): Boolean`

- [ ] **Step 1:** Shared test body, run against both implementations:
  - After `apply` of a page with two events, `events().first()` has both.
  - Applying a page that removes one leaves the other.
  - `lastRevision()` equals `page.next`.
  - `hasEverSynced()` is false before the first `apply` and true after.
  - Applying an event with a higher revision replaces the stored one.
- [ ] **Step 2:** Run `./gradlew :composeApp:jvmTest :composeApp:wasmJsTest`; expected FAIL.
- [ ] **Step 3:** Implement Room with an `EventEntity` holding primitive columns plus `lineupJson`/`listingsJson` strings, and a `meta` table for the last revision. Implement IndexedDB the way MemorChess's `JsLocalDataBaseQueryManager.wasmJs.kt` and `IndexedDbInstance.kt` do, with object stores `events` and `meta`. No schema migrations (destructive fallback), as in MemorChess.
- [ ] **Step 4:** Run `./gradlew :composeApp:jvmTest :composeApp:wasmJsTest`; expected PASS.
- [ ] **Step 5: Commit** `git add -A && git commit -m "Add local event stores for native and web"`

### Task 10: Client sync

**Files:**
- Create: `composeApp/src/commonMain/kotlin/fr/axllvy/timetabler/data/{EventsApi.kt, EventSync.kt}`, `di/Modules.kt`
- Test: `composeApp/src/nonJsTest/kotlin/fr/axllvy/timetabler/data/EventSyncTest.kt`

**Interfaces:**
- Consumes: `EventStoreLocal` (Task 9), `GET /v1/events` (Task 8).
- Produces:
  - `class EventsApi(client: HttpClient, baseUrl: String)` with `suspend fun page(since: Revision, limit: Int = 500): EventsPage`
  - `class EventSync(api: EventsApi, store: EventStoreLocal)`
  - `suspend fun syncOnce(): SyncOutcome`: loops pages while `hasMore`
  - `val state: StateFlow<SyncState>`
  - `sealed interface SyncOutcome { data object Ok; data class Offline(val cause: String) }`
  - `enum class SyncState { IDLE, SYNCING, OFFLINE }`
  - `fun serverBaseUrl(): String`: `expect`/`actual`. Web returns `""` (same origin). Native returns `"https://timetabler.axl-lvy.fr"`.

- [ ] **Step 1: Write the failing tests** with `MockEngine` and a real `RoomEventStore` (in-memory builder):
  - Three pages with `hasMore` true, true, false are all applied, and each request's `since` equals the previous page's `next`.
  - A connection failure returns `Offline` and leaves the stored events untouched.
  - A second `syncOnce` starts from the stored revision.
- [ ] **Step 2:** Run `./gradlew :composeApp:jvmTest --tests '*EventSync*'`; expected FAIL.
- [ ] **Step 3:** Implement. Koin modules wire the HttpClient engines as MemorChess does: CIO on JVM/Android, Darwin on iOS, Js on web. Sync triggers: app start, pull-to-refresh, and every 5 minutes while the app is in the foreground.
- [ ] **Step 4:** Run `./gradlew :composeApp:jvmTest --tests '*EventSync*'`; expected PASS.
- [ ] **Step 5: Commit** `git add -A && git commit -m "Pull the event feed into the local store"`

### Task 11: Events list (layout B)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/fr/axllvy/timetabler/list/{EventFilters.kt, EventsScreen.kt, EventCard.kt}`
- Test: `composeApp/src/commonTest/kotlin/fr/axllvy/timetabler/list/EventFiltersTest.kt`

**Interfaces:**
- Consumes: `EventDto`, `tonight`, `normalizeText` (Tasks 2 and 3), `EventStoreLocal.events()` (Task 9), `EventSync` (Task 10).
- Produces:
  - `enum class QuickFilter { ALL, TONIGHT, WEEKEND }`
  - `data class NightGroup(val night: LocalDate, val events: List<EventDto>)`
  - `fun filterEvents(events: List<EventDto>, query: String, filter: QuickFilter, now: Instant): List<NightGroup>`: groups by `nightDate` ascending, sorts within a night by `startsAt`, and drops nights before `tonight(now)`. `TONIGHT` keeps only `tonight(now)`. `WEEKEND` keeps the nights of a weekend Friday F and F plus one day, where F is `tonight(now)` minus one day when tonight is a Saturday, otherwise the first Friday on or after tonight.
  - `@Composable fun EventsScreen(onOpen: (EventId) -> Unit)`

- [ ] **Step 1: Write the failing tests:**
  - At Saturday 31 Oct 2026 03:00 Paris, `TONIGHT` keeps events with `nightDate` 30 Oct and drops 31 Oct.
  - `WEEKEND` on Wednesday 28 Oct keeps nights Fri 30 Oct and Sat 31 Oct only. On Saturday at 03:00 it keeps Fri 30 and Sat 31.
  - Query `"cuve"` matches an event whose lineup has `"Clara Cuvé"`; `"MOULIN"` matches venue `"La Machine du Moulin Rouge"`; `"possession"` matches the title.
  - Yesterday's night is never shown.
  - Groups are in date order, and events within a group are in start order.
- [ ] **Step 2:** Run `./gradlew :composeApp:jvmTest --tests '*EventFilters*'`; expected FAIL.
- [ ] **Step 3:** Implement `filterEvents`. Then build the UI per the approved mockup B (`.superpowers/brainstorm/*/content/event-list.html`):
  - Search field, then chips Tonight and This weekend. The "Has set times" chip arrives in plan 3.
  - Day headers like "Tonight · Fri 30 Oct", then "Sat 31 Oct", in English.
  - Each card shows the poster thumbnail (Coil, loading `serverBaseUrl() + posterPath`), the title, "venue · HH:mm → HH:mm", the first three artists plus "+N", and a grey "lineup" badge.
  - Pull to refresh calls `syncOnce`.
  - Dark theme first.
- [ ] **Step 4:** Run `./gradlew :composeApp:jvmTest --tests '*EventFilters*'`; expected PASS.
- [ ] **Step 5: Commit** `git add -A && git commit -m "Show events grouped by night with search and filters"`

### Task 12: Event page, navigation and offline web

**Files:**
- Create: `composeApp/src/commonMain/kotlin/fr/axllvy/timetabler/event/EventScreen.kt`, modify `App.kt`, create `composeApp/src/wasmJsMain/resources/{index.html, sw.js}`
- Test: `composeApp/src/jvmTest/kotlin/fr/axllvy/timetabler/AppUiTest.kt`

**Interfaces:**
- Consumes: `EventStoreLocal` (Task 9), `EventSync` (Task 10), `EventsScreen` (Task 11).
- Produces: `@Composable fun EventScreen(id: EventId, onBack: () -> Unit)`; `App()` with navigation-compose routes `events` and `event/{id}`.

- [ ] **Step 1: Write the failing UI tests** (`runComposeUiTest` on JVM, real `RoomEventStore`):
  - With one stored event, clicking its card shows the title, every lineup name, a "Tickets on DICE" button, and the text "No set times yet".
  - With an empty store, `hasEverSynced() == false`, and an `EventsApi` whose engine always fails, the list shows "Connect once to load events" and no progress indicator after the sync attempt.
- [ ] **Step 2:** Run `./gradlew :composeApp:jvmTest --tests '*AppUi*'`; expected FAIL.
- [ ] **Step 3:** Implement per the approved mockup's left screen (`event-detail.html`):
  - Hero poster, title, "Fri 30 Oct · 22:00 → 06:00 · Venue".
  - One ticket button per listing ("Tickets on DICE"), opened with `LocalUriHandler`.
  - The note "No set times yet. They usually land in the promoter's story the day before."
  - Lineup chips.
- [ ] **Step 4:** Run `./gradlew :composeApp:jvmTest --tests '*AppUi*'`; expected PASS.
- [ ] **Step 5:** Web offline. Copy insane-lineup's `sw.js` stale-while-revalidate app-shell worker (`~/IdeaProjects/insane-lineup`), extend it to cache `/v1/posters/*` cache-first, and register it in `index.html`. Do not cache `/v1/events`. Verify by hand: run the server locally (`docker run postgres:16`, then `./gradlew :server:run` with `TT_STATIC_DIR` pointing at the wasm distribution), load the page in Chrome once, switch DevTools to Offline, and reload. The list and the posters still show.
- [ ] **Step 6: Commit** `git add -A && git commit -m "Add event page, navigation and offline web shell"`

### Task 13: End-to-end test

**Files:**
- Create: `e2e/src/test/kotlin/fr/axllvy/timetabler/e2e/SyncE2eTest.kt`

**Interfaces:**
- Consumes: `TestServer` fixture (Task 8), `DiceScraper` with `MockEngine` fixtures (Task 7), `EventSync` and `RoomEventStore` (Tasks 9 and 10).

- [ ] **Step 1:** Test: start `TestServer`, run `scrapeAgenda` against the DICE fixtures, point a real `EventSync` at the server URL with a Room in-memory store, and call `syncOnce`. The local store then holds the same event ids as `EventStore.page(ZERO, 2000)`, with equal lineups.
- [ ] **Step 2:** Run `./gradlew :e2e:test`; expected PASS (the components exist, so this is a contract check; if it fails, fix the mismatch in the owning task's code).
- [ ] **Step 3: Commit** `git add -A && git commit -m "Add sync end-to-end test"`

### Task 14: Docker image, CI and deploy

**Files:**
- Create: `Dockerfile`, `.github/workflows/{check.yml, deploy.yml, pr_name_check.yml}`
- Modify (private home-lab repo, on a branch `timetabler`): the app's compose entries, deploy hook and backup.

**Interfaces:**
- Consumes: server env vars (Global Constraints), `/health` (Task 8).

- [ ] **Step 1:** `Dockerfile`: copy MemorChess's two stages. The runtime user is `timetabler` (uid 10001). Set `TT_STATIC_DIR=/app/www`, `TT_POSTER_DIR=/data/posters` and `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`, and keep the wget healthcheck. No Chromium yet (plan 2).
  Run: `docker build -t timetabler-server:dev .` and `docker run --rm -e TT_DB_URL=... timetabler-server:dev` against a local `postgres:16`.
  Expected: `/health` 200; `/` serves the app.
- [ ] **Step 2:** Workflows.
  - `check.yml` is MemorChess's three-tier workflow minus the benchmark, Sonar and translations jobs. Cheap tier: `ktfmtCheck`, `:shared:jvmTest :server:test :e2e:test :composeApp:jvmTest`, multiplatform compile. Full tier adds `:composeApp:wasmJsTest`, Android unit tests and iOS compile.
  - `pr_name_check.yml` is copied as is.
  - `deploy.yml` is copied with the image `ghcr.io/axl-lvy/timetabler-server`, the secrets `TIMETABLER_DEPLOY_WEBHOOK_TOKEN`, `CF_ACCESS_CLIENT_ID`, `CF_ACCESS_CLIENT_SECRET`, and the hook URL `https://deploy-memorchess.axl-lvy.fr/hooks/timetabler`.
- [ ] **Step 3:** Home-lab entries (private repo): the server and its own Postgres on private networks, a memory limit, healthchecks, the deploy hook and a nightly database backup.
- [ ] **Step 4:** Commit in both repos: `git add -A && git commit -m "Add Docker image, CI and deploy workflow"` in TimeTabler, `git commit -m "Add TimeTabler app entries"` in home-lab.
- [ ] **Step 5:** Hand the owner the manual list, which the agent cannot do:
  - Create the GitHub repo and push.
  - Set the GitHub secrets.
  - Fill `.env` with `openssl rand -hex 32`.
  - Set up the tunnel hostname and the deploy webhook (details in the home-lab repo).
  - Merge the home-lab branch.

### Task 15: Formatting commit

- [ ] **Step 1:** Run `./gradlew ktfmtFormat`, then `./gradlew ktfmtCheck`; expected BUILD SUCCESSFUL.
- [ ] **Step 2:** `git commit -am "Apply ktfmt formatting"` (formatting changes only).
