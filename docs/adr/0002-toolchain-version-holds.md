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

## Addendum, 2026-10-30: JDK 25 with a JVM 21 target

The walking-skeleton plan asked for a JDK 21 toolchain. The build declares no Gradle toolchain, so
it compiles with the JDK that runs Gradle: JDK 25 locally, in CI (`actions/setup-java`, Temurin 25)
and in the Docker build stage (`eclipse-temurin:25-jdk`). The server runs on `eclipse-temurin:25-jre`.
Every module still compiles to JVM 21 bytecode (`jvmTarget` and `sourceCompatibility` 21), the
level the Android app is built against. MemorChess makes the same choice.

Decision: keep JDK 25 to build and run, and JVM 21 as the bytecode target; no toolchain is added.
The JDK 24+ restricted native access warnings are already handled where tests load Skiko
(`--enable-native-access` in `composeApp/build.gradle.kts`). A higher target is raised in every
module at once, after checking `:androidApp:assembleDebug`.

## Links

- MemorChess `docs/adr/0002-toolchain-version-holds.md`
