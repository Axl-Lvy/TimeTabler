# TimeTabler

[![Quality gate](https://sonarcloud.io/api/project_badges/measure?project=Axl-Lvy_TimeTabler&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=Axl-Lvy_TimeTabler)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=Axl-Lvy_TimeTabler&metric=coverage)](https://sonarcloud.io/summary/new_code?id=Axl-Lvy_TimeTabler)

The exact lineup and set times of music events, starting with Paris club nights. Lineups come from
DICE (and later Shotgun and festival feeds); set times come from the timetables promoters post in
their Instagram stories.

Kotlin Multiplatform app (Android, iOS, web) with a Ktor server that scrapes events into Postgres and
feeds every client's offline store.

## Architecture

TimeTabler is a Kotlin Multiplatform client (Android, iOS and web) fed by a Ktor server. Only the
server talks to ticketing sites; each client keeps its own local copy of the events and stays
usable offline.

```mermaid
flowchart LR
    subgraph Client["Client (Android / iOS / Web)"]
        UI["Compose UI\nevents list, event page"]
        LocalDB[("Room / IndexedDB\nlocal events")]
        UI --> LocalDB
    end

    subgraph Server["Server (Ktor)"]
        Scraper["Scrapers\n(krontab schedule)"]
        Api["/v1/events, /v1/posters,\n/v1/admin, web bundle"]
    end

    Postgres[("Postgres\nvenues, events, listings")]
    Dice["DICE\npublic pages"]
    Shotgun["Shotgun\n(headless Chromium, planned)"]
    Oidc["OIDC provider\n(Logto)"]
    Vision["Local vision model\n(Ollama, planned)"]

    Client -- "pull changed events since revision" --> Api
    Scraper -- "page data, 1 req / 2 s" --> Dice
    Scraper -.-> Shotgun
    Scraper --> Postgres
    Api --> Postgres
    Client -- "admin sign in (web)" --> Oidc
    Api -- "admin token keys (JWKS)" --> Oidc
    Server -. "timetable screenshots" .-> Vision
```

- The **server** scrapes DICE's public pages on a schedule, keeps one row per event in
  **Postgres**, and gives every change a new revision.
- The **client** pulls the events changed since its last revision into its local database (Room
  on Android and iOS, IndexedDB on web) and renders only from there, so it works offline.
- Ticket buttons open the DICE or Shotgun page; the app itself never calls those sites.
- Only the owner signs in, through Logto, and admin routes need a token with the `admin` scope.
- Dashed parts are planned: Shotgun scraping, admin editing of timetables, and reading set times
  from story screenshots with a local vision model.

## Run locally

```sh
git config core.hooksPath .githooks        # format check on commit
docker run -d --name tt-postgres -e POSTGRES_PASSWORD=tt -e POSTGRES_USER=tt -e POSTGRES_DB=tt -p 5432:5432 postgres:16
TT_DB_URL=jdbc:postgresql://localhost:5432/tt TT_DB_USER=tt TT_DB_PASSWORD=tt ./gradlew :server:run
./gradlew :composeApp:wasmJsBrowserDevelopmentRun   # web client
./gradlew :androidApp:installDebug                  # Android client
```

Admin routes refuse every request until the server has `TT_AUTH_ISSUER` and `TT_AUTH_AUDIENCE`
(and `TT_AUTH_JWKS_URL` when the keys are not at `<issuer>/jwks`), and the client signs in only once
`ADMIN_OIDC` in `composeApp/.../auth/AuthConfig.kt` holds the tenant instead of placeholders.

## License

GPL-3.0, see [LICENSE](LICENSE).
