# ADR 0001: Three tier CI checks (noop / cheap / full)

## Status

Accepted, 2026-10-29. Carried over from MemorChess ADR 0001.

## Context

TimeTabler targets Android, iOS, wasmJs and a JVM server. Running every platform's tests on every
pull request costs minutes even when a change touches only documentation or common code that no
platform-specific job can regress. MemorChess measured this and settled on three tiers; TimeTabler
has the same module layout and inherits the same trade-off.

## Decision

A `detect` job classifies every run with `dorny/paths-filter` against the diff:

- **noop**: no code or build file touched. Only `detect` and `check-status` run.
- **cheap**: code in `commonMain`, `jvmMain`, `:server`, `:shared` or `:e2e` (or their tests), and no
  platform source set, manifest, Gradle script or version catalog. Runs `format-check`,
  `jvm-tests` (JVM tests with Kover coverage, Sonar inline), `compile-multiplat` and
  `publish-test-results`.
- **full**: anything in `androidApp`, `androidMain`, `iosMain`, `wasmJsMain`, `nonJsMain`, a
  manifest, any `*.gradle.kts`, `gradle/libs.versions.toml` or `.github/workflows/check.yml`. Adds
  `wasm-tests`, `ios-tests`, `android-build` and a standalone `sonar-analysis`, and drops
  `compile-multiplat` since the test jobs compile every target. `android-build` stands in for
  MemorChess's emulator tests until TimeTabler has instrumented tests.

A `translations-check` job runs outside the tiers, whenever a `composeResources/values*` directory
or its checker changes: `.github/scripts/check_translations.py` fails when a locale's keys differ
from the base `values/` keys or their order. A forced full run includes it and a forced noop skips
it.

The daily cron and the `- [x] Force running tests` PR body checkbox force full; `[skip-tests]` in
the title forces noop. Branch protection requires the single aggregator check `All checks passed`.

## Consequences

Documentation and common-code pull requests stay fast. A wrong filter rule can classify a
platform-sensitive change as cheap; `compile-multiplat` on the cheap tier is the safety net for
`expect`/`actual` regressions, and the daily cron catches the rest within a day.

## Links

- MemorChess `docs/adr/0001-three-tier-ci-checks.md`
