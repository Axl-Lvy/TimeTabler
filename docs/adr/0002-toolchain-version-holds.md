# ADR 0002: Hold the Gradle wrapper below 9.7.0

## Status

Accepted, 2026-10-29. Carried over from MemorChess ADR 0002. Enforced by `packageRules` in
`renovate.json`.

## Context

Gradle 9.7.x exhausts the daemon heap while storing the configuration cache entry for the
Kotlin/Wasm browser tasks (`KotlinWebpack`, `KotlinJsTest`), so `:composeApp:wasmJsBrowserDevelopmentRun`
and `:composeApp:wasmJsTest` die with `Java heap space` before any task runs. The entry is discarded
anyway, since those tasks are not configuration-cache compatible; 9.6.1 stores and discards it in
about 25 seconds. MemorChess re-checked Gradle 9.8.2 on 2026-10-25 with the same result.

## Decision

The wrapper stays on 9.6.1. Renovate may not propose 9.7.0 or later.

## Consequences

Gradle fixes after 9.6.1 are unavailable until the hold is lifted. Lift it once a Gradle release
builds `:composeApp:wasmJsTest` with the configuration cache enabled.

## Links

- MemorChess `docs/adr/0002-toolchain-version-holds.md`
