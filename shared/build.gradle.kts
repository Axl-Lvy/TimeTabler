import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKmpLibrary)
  alias(libs.plugins.kotlinX.serialization.plugin)
  alias(libs.plugins.ktfmt)
  alias(libs.plugins.kover)
}

kotlin {
  androidLibrary {
    namespace = "fr.axllvy.timetabler.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
    compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
  }

  jvm()

  iosArm64()
  iosSimulatorArm64()

  @OptIn(ExperimentalWasmDsl::class) wasmJs { browser() }

  sourceSets {
    commonMain.dependencies {
      // api: wire types expose LocalDate and Json in their public signatures.
      api(libs.kotlinx.datetime)
      api(libs.kotlinx.serialization.json)
    }

    // kotlinx-datetime reads time zones from js-joda in the browser.
    wasmJsMain.dependencies { implementation(npm("@js-joda/timezone", "2.22.0")) }

    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotest.assertions)
    }

    sourceSets.all { languageSettings.optIn("kotlin.time.ExperimentalTime") }
  }
}

ktfmt { googleStyle() }

// Same engine as composeApp, which merges this report into its own.
kover { useJacoco("0.8.14") }
