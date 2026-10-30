# TimeTabler: design (step 1)

Date: 2026-10-29. Status: draft, awaiting approval.

## Intent

TimeTabler gives the exact lineup and, ideally, the set times of music events, starting with Paris club nights. DICE, Shotgun and RA list lineups but almost never set times. Set times and last-minute changes appear in Instagram stories, often the day before, and today the owner screenshots that story and uses the camera roll as the night's timetable. TimeTabler replaces that screenshot.

Audience: the owner and friends first, the public later. The friends phase may be rough and partly hand-run, but nothing that only works at small scale may be baked in.

Prior art: the owner's `insane-lineup` app (`~/IdeaProjects/insane-lineup`, one hardcoded festival on Supabase). Its timetable, artist view, SoundCloud links, favorites, friends overlay and notifications are liked and will return in later steps. TimeTabler is built step by step; this document covers **step 1** only.

UX matters: the app must be very easy to use, on a phone, at night, possibly offline.

## Step 1 goals

1. A list of **every** Paris event scraped from DICE and Shotgun, as poster cards grouped by night (event image, name, venue, hours, first few artists, a "set times" or "lineup" badge). Search by artist, venue or event name. Quick filters: Tonight, This weekend, Has set times.
2. An event page: poster, date, hours, venue, ticket links back to every source page (DICE and/or Shotgun), the scraped lineup, and, once known, the set times as a **running order** (chronological list, current set highlighted, stage shown per row).
3. The owner uploads a story screenshot to an event. A local vision model extracts the sets (artist, stage, start, end). The result **goes live immediately**; the owner fixes mistakes afterwards in an edit screen. A newer screenshot replaces the previous timetable for that event. The owner can also type a timetable by hand with no screenshot; if spike 1 fails, hand entry is how step 1 ships and extraction joins later.
4. Viewers need no account. Only the owner logs in (Logto), and only an admin may upload, edit, or merge events.
5. Every client works offline after its last sync.

## Non-goals for step 1 (planned later, roughly in this order)

- Stage-column timetable (Insane style) for festivals and multi-room nights.
- Artist page with photo and SoundCloud link.
- Favorites, friends overlay, notifications (ntfy exists in the homelab).
- Festival sources: hand-kept festival sites and official app feeds (Appmiral, Woov, GreenCopper/Aloompa, see porkcharsui/clashfinder).
- Resident Advisor (`ra.co/graphql`, apollo-kotlin).
- Uploads by other users, and moderation before going public.
- Timetables split across several screenshots (one per stage or per day). In step 1, one screenshot is the whole timetable.
- Other cities.

## Anti-goals

- Scraping Instagram, or any paid service.
- Using DICE's widget `x-api-key` on `events-api.dice.fm` without asking the owner first.
- Copying code from unlicensed or GPL-2.0-only repos (BetterShotgun, sceneland, the RA scraper, giggity). They are reference only for endpoints and queries.
- Core logic in n8n.
- A blocking upload request that waits for the vision model.

## Decided by the owner

| Topic | Decision |
|---|---|
| Name | TimeTabler (app and repo) |
| Repo | Public, `github.com/Axl-Lvy/TimeTabler` |
| License | GPL-3.0 |
| Event sources (step 1) | DICE and Shotgun, whole Paris agenda |
| Upload | Owner only, from the event page |
| Review | Extraction goes live immediately, owner fixes after |
| Auth | Logto Cloud, **separate tenant** from MemorChess. No Cloudflare Access on this app, because it will be public |
| Events list | Layout B: poster cards with headliners |
| Timetable view | Layout B: running order list |
| Stack | Mirror MemorChess (see Constraints) |
| Hosting | Mini PC in home-lab, same pattern as MemorChess |
| Distribution (step 1) | Web app first: the owner and friends use the web app. Android and iOS build and pass CI, no store, signing or TestFlight work |
| Owner's upload device | Android phone, through the web app in Chrome |
| If no vision model passes spike 1 | Step 1 ships with hand-typed timetables; extraction joins when a model passes |
| If Shotgun blocks the mini PC | Step 1 ships with DICE only; Shotgun, Chromium in the image and cross-source matching move to a later step |

## Constraints

### Stack (mirror `~/IdeaProjects/MemorChess/A`)

- Kotlin Multiplatform plus Compose Multiplatform. Targets: Android, iOS, wasmJs. A `jvm` target exists for tests and e2e, as in MemorChess; a desktop app ships only if it comes for free.
- Modules: `shared` (pure KMP, used by the server: domain types, wire DTOs, night/time rules, title and name normalisation), `composeApp`, `androidApp`, `server` (Ktor, Postgres), `e2e`.
- Version catalog, versions aligned with MemorChess (`gradle/libs.versions.toml`). Gradle wrapper held below 9.7.0 (MemorChess ADR 0002, wasm configuration-cache heap exhaustion).
- Client storage: Room on Android, iOS and JVM; IndexedDB (`com.juul.indexeddb`) on web, behind one query abstraction, as in MemorChess.
- Server: raw JDBC with HikariCP and an idempotent `schema.sql` applied at boot, as in MemorChess. No migration tool until real user data exists.
- Koin, Kermit, kotlinx.serialization, krontab for scheduled jobs.
- `@JvmInline value class` for domain ids and constrained values; `kotlin.time.Instant` and `Duration` for times; `kotlinx-datetime` only for calendar types (`LocalDate`, `TimeZone`). Primitive fields at the storage boundary, converted in mappers.
- Tests: Kotest assertions, no mocks (Ktor `client-mock`, test fixtures, Testcontainers Postgres with `api.version=1.40`). No commas in backtick test names.
- ktfmt Google style. A **versioned** pre-commit hook (`core.hooksPath` pointing at a tracked directory) running `ktfmtCheck`; MemorChess's hook is not versioned, so this one is new.
- Conventional Commit PR titles, PR template, worktrees under `.claude/worktrees/`, ADRs in `docs/adr/NNNN-kebab-title.md`, Renovate config like MemorChess, three-tier CI (ADR 0001: noop, cheap, full; single required check "All checks passed").
- `.gitignore` adds `.superpowers/` and `.claude/worktrees/`, and does **not** exclude `docs/superpowers/` (unlike MemorChess), so specs and plans are committed.
- UI references: JetBrains/kotlinconf-app and joreilly/Confetti (Apache-2.0).

### Scraping libraries

- Ktor client plus kotlinx.serialization for HTTP, `__NEXT_DATA__` and JSON-LD.
- jsoup on the server (parsing stays server-side, so ksoup is not needed).
- playwright-java for Shotgun; kdriver is the fallback if Playwright gets flagged.

### Hosting

- Image `ghcr.io/axl-lvy/timetabler-server:latest` (unpinned, required by the auto-deploy webhook) plus `:<sha7>`. Two-stage Dockerfile like MemorChess; the runtime stage also carries Chromium and its system deps for Playwright.
- The server serves the wasmJs bundle as static files on the same origin, with MemorChess's cache rules (hashed assets cached a year, everything else `no-cache`).
- Deployed on the owner's home server next to its own Postgres, which only the server can reach. Public hostname `timetabler.axl-lvy.fr` through a tunnel, with no login wall in front.
- Auto-deploy: the deploy workflow pushes the image and calls the home server's deploy webhook.
- Nightly backup of the database.
- **Memory.** The home server's RAM is shared with other services and the local vision model, so the server container gets a memory limit (starting point 1.5 GB, to be measured). Chromium is launched for a scrape run, uses one page at a time, and is closed when the run ends.
- The exact compose, network, tunnel, webhook and backup configuration lives in the private home-lab repository.

## How it works

### Domain (my calls on names)

- **Venue:** canonical name, address, aliases (per source names), source ids.
- **Event:** one real-world night. Starts at, ends at, night date, venue, title, poster, lineup (ordered artist names), list of **listings**.
- **Listing:** one source page for an event: source (DICE or Shotgun), external id, URL, raw title, start, end, status (on sale, sold out, cancelled), lineup, poster URL, last seen, content hash.
- **Screenshot:** image file, event, uploader (Logto `sub`), uploaded at, extraction status (queued, running, done, failed), model name, raw model output.
- **Timetable:** the sets of an event, either from the latest successful screenshot with manual edits on top, or typed by hand with no screenshot.
- **Set:** artist name as read, matched lineup artist (optional), stage (optional), start, end (optional).

Every screenshot keeps its uploader so that opening uploads to the public later is a policy change (moderation queue, trust levels), not a schema change.

### Night and time rules (in `shared`)

- All local times are `Europe/Paris`.
- **Night date** of an event = local date of (start minus 6 hours). A set starting Saturday 00:30 belongs to Friday's night. "Tonight" = the night date of (now minus 6 hours), so at 03:00 on Saturday "Tonight" still shows Friday's night.
- A screenshot gives wall-clock times only. Each time resolves to the first instant at or after (event start minus 2 hours) with that wall-clock time, so `23:00, 01:00, 03:00` roll over midnight correctly. Insane's "before 12:00 add 24 h" string rule is not carried over.

### Scraping (server only)

- **DICE:** plain HTTP. `dice.fm/browse/paris` and its pages (30 events each) give the agenda from `__NEXT_DATA__`; each event page's schema.org `MusicEvent` JSON-LD gives the `performer` list. No widget API key.
- **Shotgun:** headless Chromium (Playwright) because plain HTTP gets 429 from the Vercel checkpoint. `shotgun.live/fr/cities/paris?page=30` gives the whole city agenda on one page; event pages carry JSON-LD and link performers as `/fr/artists/...`. `robots.txt` allows all.
- **Politeness:** at most one request every 2 seconds per host, a descriptive User-Agent, conditional requests and a content-hash cache so unchanged pages are not re-parsed. Prefer page data (`__NEXT_DATA__`, JSON-LD) over internal APIs.
- **Schedule (krontab, inside the server):** the full agenda from both sources once a day at night; event pages for events in the next 48 hours every 3 hours. A scraper failure is logged and leaves the previous data in place.
- **Change tracking:** each listing keeps its content hash and lineup history, so a later notification step can detect "lineup changed" without new scraping work.
- **Window:** events from yesterday to 60 days ahead are kept active; older events stay in the database but leave the client sync window.

### Matching DICE and Shotgun listings into one event

- Candidates: same night date and same venue (venue matched through normalised name plus the alias table).
- Score: title similarity after normalisation (case, accents, punctuation, stop words like "presents") and lineup overlap. Above a threshold, the listings join one event; otherwise they stay separate events.
- The admin can merge two events or split a listing off. Manual merges and splits are remembered and win over the automatic matcher on later scrapes.
- Venue aliases start as a hand-kept list in the repo, grown when the admin merges.

### Screenshot extraction

- **Flow:** the admin uploads from the event page. The server stores the image, creates a queued job, and answers immediately. A worker runs the vision model, writes the timetable, and the result reaches clients through the normal sync. The event page shows "reading screenshot…" until then. An iGPU vision pass may take a minute or more.
- **Model:** a local vision model through Ollama on the mini PC (Radeon 780M, Vulkan, ~17 GiB usable UMA). Candidates: `qwen2.5vl` and Qwen3-VL at a size that fits. Vision on Vulkan is unverified; spike 1 decides the model.
- **Client library:** langchain4j's Ollama model, which takes images and a JSON-schema response format mapped to typed data classes: a list of sets, each with artist, optional stage, start `HH:mm`, optional end `HH:mm`.
- **Request context:** the event's known lineup, title, venue, date and opening hours go into the prompt, and `artist` is constrained to the lineup names, with a separate list for names not in the lineup (see spike 1).
- **Gateway:** either the home server's LLM gateway, if it passes images through, or Ollama directly; spike 1 decides. Going direct breaks the single-gateway rule, so it gets an ADR.
- **Post-processing:** times are resolved with the night rules above. Each artist name is matched to the scraped lineup (normalised fuzzy match); the name as read is kept either way.
- **Replacement:** a newer successful extraction replaces the event's timetable. Manual edits made on the older one are discarded with it (my call; a warning in the edit screen says so).
- **Images:** resized server-side before inference (longest side capped, size chosen in spike 1).

### Admin edit screen

Available in the app only when the logged-in account has the admin role. It lets the admin:

- Create a timetable from scratch with no screenshot.
- Edit, add, delete and reorder sets (artist, stage, start, end).
- Re-run extraction on the current screenshot and see the original image next to the result.
- Merge and split events.

### Auth

- Logto Cloud, a new tenant for TimeTabler. Two app registrations as in MemorChess: one Native app (Android, iOS, JVM) and one SPA app (wasm, needed for token-endpoint CORS). An API resource with its own audience, so access tokens are JWTs.
- The server verifies `iss`, `aud`, `exp` and the signature against the tenant's JWKS, as MemorChess's `JwtAuth.kt` does.
- **New compared with MemorChess:** a role check. The `admin` role in Logto grants an `admin` scope on the API resource; upload, edit, merge and split routes require that scope. MemorChess has no role check, so this is new code.
- Reading routes need no token.

### Server API (shape, names are the builder's)

- `GET /v1/events?since=<revision>&limit=`: pages of events, listings, lineups and timetables changed since a server-assigned revision, ordered by revision, plus tombstones for events that left the window or were merged. Read-only for clients.
- `GET /v1/posters/{id}`: poster images, fetched from DICE or Shotgun once, resized and cached by the server.
- Admin routes: upload a screenshot, read extraction status, edit a timetable, merge and split events.
- `GET /health`: always 200 while the server runs, with the last run of each scrape job (attempt and success times, failures in a row, pages, listings, upserts, lineups, last agenda day) as JSON for the home-lab exporter.

### Clients

- One-way sync: the client stores the last revision, pulls pages on launch, on pull-to-refresh and every few minutes while open, and keeps everything in Room or IndexedDB. Nothing is written by viewers, so MemorChess's two-way outbox and conflict resolver are not needed in step 1.
- Posters are cached locally with the event data so the list looks right offline.
- First launch with no network shows an empty state that explains it needs one sync.
- Web: the service-worker app-shell cache pattern from insane-lineup, so the web app opens offline after its first visit.
- Screens: events list (layout B), event page (lineup state and running-order state), login (admin only, reached from a discreet entry point), admin edit screen.
- **Web first.** The web app is what the owner and friends use in step 1, so its path gets the most care: service worker, IndexedDB, the Logto SPA login under COOP/COEP, and the file picker in Chrome on Android (the owner's upload device). Android and iOS share the UI and must build and pass CI, but step 1 has no store, signing or TestFlight work.

### Web headers (my call)

The wasm bundle is served with COOP `same-origin` and COEP `credentialless`, which MemorChess's production server does not send. Because posters are proxied through `/v1/posters`, no cross-origin images are needed and COEP does not break them.

## Spikes (run only after this design is approved; throwaway code)

### Spike 1: vision model on the mini PC

Pull candidate models into Ollama, run 5 real timetable story screenshots, compare the output with hand-typed truth. Also check whether LiteLLM passes images through.

Setup (2026-10-29): Ollama 0.40.12 on Vulkan, models pulled with default quantisation, called on `/api/chat` with a JSON-schema `format`, temperature 0, prompt asking for artist, stage, start, end (end null if not shown). Both models load at 100% GPU, so **vision on Vulkan works**. Script: throwaway, in the session scratchpad.

| Image | Model | Loaded size | Latency | Names | Start times | End times |
|---|---|---|---|---|---|---|
| synthetic 1080x1920, 2 rooms, 5 sets | `qwen2.5vl:7b` | 5.7 GB | 52 s (7 s load) | 5/5 | 5/5 | 5/5 |
| same | `qwen3-vl:8b` | 6.6 GB | 147 s (7 s load) | 4/5 (`NARCIS`) | 5/5 | 5/5 |
| `01-miamao-orka.png` (real story, 591x1069, 6 sets, start times only, small `23H00` labels) | `qwen2.5vl:7b` | 5.7 GB | 41 s | 6/6 | 5/6 (Keros `06:00` instead of `05:00`) | invented ends from the next start, one wrong; `"null"` as a string for stage |
| same | `qwen3-vl:8b` | 6.6 GB | 88 s (warm) | 6/6 | 6/6 | correctly null; put the event name `ORKA` in stage |

Truth for `01-miamao-orka.png` (Mia Mao, ORKA, 29 Oct 2026, 23:00 to 06:00): Anamorfoz (live) 23:00, Night Owl 00:00, Skollaris 01:30, Sopik 02:30, DJ Ratz 03:45, Keros 05:00.

Observations so far:

- Post-processing must derive missing ends from the next start (last set ends at the event end), and drop a stage that equals the event title or appears on every set.
- A `qwen3:8b` text model from another client was loaded at the same time during the synthetic run, which may explain part of `qwen3-vl`'s 147 s.
- Screenshots 2 to 5: not run. The owner chose to start building and finish this spike later.

**Status: paused, model not yet chosen.** `qwen3-vl:8b` leads on the one real screenshot. Before the extraction pipeline is built, the spike resumes with a **lineup-aware request**: the prompt carries the event title, venue, date, opening hours and the scraped lineup; the schema's `artist` field becomes an enum of the lineup names, with unknown names (secret guests, b2b, changes) returned in a separate free-text list. That variant is what gets scored against the pass bar. Until then, step 1 is built with hand-typed timetables first, and extraction plugs into the same timetable model when the spike passes.

**Pass bar:** at least 90% of set times correct across the 5 screenshots, and under 2 minutes per image.

**If no model passes:** step 1 ships with hand-typed timetables (the edit screen already covers it) and the extraction pipeline moves to a later step, revisited when a better model or the big-GPU PC is available. No paid or cloud vision service.

Decision recorded here: _model, image size, gateway option 1 or 2, or "no model passed"._

### Spike 2: Shotgun from the mini PC

Playwright with Chromium in a container on the mini PC, against `shotgun.live/fr/venues/mia-mao` and `shotgun.live/fr/cities/paris?page=30`. Record: does the Vercel checkpoint pass from that IP, time to first content, peak container memory.

**If it fails** with both Playwright and kdriver: step 1 ships with DICE only. Shotgun scraping, Chromium in the runtime image, and the DICE/Shotgun matcher with merge and split move to a later step. No scraping from another host.

**Result (2026-10-29): pass.** Run from a throwaway `mcr.microsoft.com/playwright/python:v1.49.0-noble` container on the mini PC, `--memory 2g`, default headless Chromium user agent:

| Page | Status | Time | Content |
|---|---|---|---|
| `/fr/venues/mia-mao` | 200, no checkpoint | 1.6 s | 25 event links |
| `/fr/cities/paris?page=30` | 200, no checkpoint | 5.0 s | 421 event links, 3.2 MB HTML |
| one event page | 200 | 0.5 s | JSON-LD blocks `Brand`, `WebSite`, `MusicEvent`, `BreadcrumbList`; performers linked as `/fr/artists/...` |

Whole run 7.4 s; container peak memory 948 MB (cgroup `memory.peak`, includes Python and pip). Plain `curl` from the same host still gets 429 from Shotgun, so the browser is required.

DICE from the mini PC with plain HTTP: venue page `dice.fm/venue/trabendo-eo56` 200 with `__NEXT_DATA__`. `dice.fm/browse/paris` redirects (301) to `dice.fm/browse/paris-5b23e8a0e63cc224a4c36a2d`, whose `pageProps` hold `events` and a `nextCursor`, so the agenda paginates by cursor, not page number.

The cursor's next page is only reachable through `api.dice.fm/unified_search`, which answered 403 on 2026-10-30 even with DICE's `Origin` and `Referer` headers, so it is not used. A browse page shows at most 24 events; when a day overflows, the scraper reads that day's sub-genre pages (`secondaryFilters`), which together held every event of Halloween 2026 (party 22/22, gig 21/21).

## Prerequisites the owner does by hand

- Create the public GitHub repo `Axl-Lvy/TimeTabler`.
- Create the Logto tenant, the Native and SPA app registrations, the API resource, the `admin` role and scope, and assign the role to the owner's account.
- Set up hosting on the home server (tunnel hostname, deploy webhook, secrets); details in the private home-lab repository.
- Provide 5 timetable story screenshots for spike 1.

## Left to the builder

- Exact table layout, route names, DTO names, module package names.
- Matching threshold values, tuned on real data.
- Scrape timings within the schedule above.
- UI details within the two chosen layouts; theming (dark first fits the use case).
- Whether the desktop JVM app ships (only if it costs nothing extra).

## Licenses of references

MIT or Apache-2.0, usable: porkcharsui/clashfinder (MIT), judell/community-calendar (Apache-2.0, not used without asking), JetBrains/kotlinconf-app and joreilly/Confetti (Apache-2.0). Reference only, no code copied: Tangui-Gouirand/BetterShotgun, sceneland, djb-gt/resident-advisor-events-scraper (no license), giggity (GPL-2.0-only).
