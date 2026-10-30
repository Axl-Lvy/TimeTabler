plugins {
  // Declared here so each plugin is loaded once, not in every subproject's classloader.
  alias(libs.plugins.androidApplication) apply false
  alias(libs.plugins.androidKmpLibrary) apply false
  alias(libs.plugins.composeMultiplatform) apply false
  alias(libs.plugins.composeCompiler) apply false
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.kotlinJvm) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.kover) apply false
  alias(libs.plugins.sonar)
}

sonar {
  properties {
    property("sonar.projectKey", "Axl-Lvy_TimeTabler")
    property("sonar.organization", "axl-lvy")
    property("sonar.host.url", "https://sonarcloud.io")

    // :shared is merged into composeApp's report through kover(project(":shared")). :server is
    // not a dependency of composeApp, so it has its own report, without the Jvm suffix that only
    // multiplatform modules get.
    property(
      "sonar.coverage.jacoco.xmlReportPaths",
      "${projectDir}/composeApp/build/reports/kover/reportJvm.xml," +
        "${projectDir}/server/build/reports/kover/report.xml",
    )

    property(
      "sonar.exclusions",
      "**/build/**,**/generated/**,**/*.gradle.kts,**/R.java,**/BuildConfig.java,**/*Manifest*.xml",
    )

    // Kover cannot instrument Kotlin/Wasm or Kotlin/Native, and @Composable functions emit
    // synthetic branches JaCoCo cannot filter. The entrypoints wire real platform stacks that
    // no test starts. CI scripts under .github are checked by the job that runs them. All of
    // these stay analyzed for code quality.
    property(
      "sonar.coverage.exclusions",
      "**/build/**,**/generated/**,**/*.gradle.kts,**/R.java,**/BuildConfig.java,**/*Manifest*.xml," +
        "**/wasmJsMain/**,**/iosMain/**,**/androidMain/**,**/ui/**,**/main.kt,**/Main.kt,.github/**",
    )
  }
}
