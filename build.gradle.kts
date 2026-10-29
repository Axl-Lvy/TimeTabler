plugins {
  // Declared here so each plugin is loaded once, not in every subproject's classloader.
  alias(libs.plugins.androidApplication) apply false
  alias(libs.plugins.androidKmpLibrary) apply false
  alias(libs.plugins.composeMultiplatform) apply false
  alias(libs.plugins.composeCompiler) apply false
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.kotlinJvm) apply false
  alias(libs.plugins.ksp) apply false
}
