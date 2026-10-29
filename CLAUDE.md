# CLAUDE.md

TimeTabler: lineups and set times of music events, Paris first. Design and plans live in
`docs/superpowers/specs/` and `docs/superpowers/plans/`; read the spec before changing behaviour.

## Modules

- `shared`: pure KMP code used by clients and server (domain ids, night rules, wire DTOs).
- `composeApp`: Compose Multiplatform UI and client data (Room on `nonJs`, IndexedDB on `wasmJs`).
- `androidApp`: thin Android shell. `iosApp/`: Xcode project embedding `ComposeApp`.
- `server`: Ktor + Postgres. Scrapes sources on a krontab schedule, serves the event feed, posters
  and the wasm bundle. Configured only through `TT_*` env vars.
- `e2e`: JVM tests running the client against a real server.

## Commands

| Task | Command |
|---|---|
| Server tests (needs Docker) | `./gradlew :server:test` |
| Shared tests | `./gradlew :shared:jvmTest` |
| Client tests | `./gradlew :composeApp:jvmTest` and `:composeApp:wasmJsTest` |
| End-to-end | `./gradlew :e2e:test` |
| Format | `./gradlew ktfmtFormat` |

Run one Gradle build at a time. Android builds need `local.properties` with `sdk.dir`.

## Conventions

- Develop in a worktree under `.claude/worktrees/`.
- Domain ids and constrained values are `@JvmInline value class`; times are `kotlin.time.Instant` and
  `Duration`; `kotlinx-datetime` only for calendar types. Storage fields stay primitive.
- Local dates and times are `Europe/Paris`. An event's night is the local date of its start minus 6 h.
- KDoc: one or two lines on public declarations, what not how. No history or issue references.
- Tests: `kotlin.test` with Kotest assertions, no mocking libraries (Ktor `MockEngine`,
  Testcontainers, fixtures). Arrange, act, assert separated by blank lines. No commas in backtick
  test names.
- Scraping: page data only (`__NEXT_DATA__`, JSON-LD), one request per 2 s per host. Never use
  DICE's `events-api.dice.fm`, `api.dice.fm` or an API key without asking the owner.
- PR titles are Conventional Commits; commit messages follow `.github/git-commit-instructions.md`.
- The Gradle wrapper stays below 9.7.0 (`docs/adr/0002-toolchain-version-holds.md`).
