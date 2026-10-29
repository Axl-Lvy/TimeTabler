import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKmpLibrary)
  alias(libs.plugins.kotlinX.serialization.plugin)
  alias(libs.plugins.ktfmt)
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

    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotest.assertions)
    }

    sourceSets.all { languageSettings.optIn("kotlin.time.ExperimentalTime") }
  }
}

ktfmt { googleStyle() }
