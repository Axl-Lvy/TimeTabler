import com.ncorti.ktfmt.gradle.tasks.KtfmtBaseTask
import java.io.File
import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKmpLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.kotlinX.serialization.plugin)
  alias(libs.plugins.ktfmt)
  alias(libs.plugins.room)
  alias(libs.plugins.ksp)
}

kotlin {
  androidLibrary {
    namespace = "fr.axllvy.timetabler.library"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
    compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    // Packages Compose Multiplatform resources into the AAR (CMP-9547).
    androidResources.enable = true
  }

  jvm()

  listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
    iosTarget.binaries.framework {
      baseName = "ComposeApp"
      isStatic = true
    }
  }

  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    outputModuleName = "composeApp"
    browser {
      commonWebpackConfig { outputFileName = "composeApp.js" }
      testTask { useKarma { useFirefoxHeadless() } }
    }
    binaries.executable()
  }

  // withAndroidTarget() only matches the legacy Android target, so the AGP-provided
  // androidLibrary target is matched by platform type instead.
  @OptIn(ExperimentalKotlinGradlePluginApi::class)
  applyDefaultHierarchyTemplate {
    common {
      group("nonJs") {
        withCompilations { it.target.platformType == KotlinPlatformType.androidJvm }
        withJvm()
        group("ios") { withIos() }
      }
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(projects.shared)

      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.material3)
      implementation(libs.compose.ui)
      implementation(libs.compose.components.resources)
      implementation(libs.compose.components.ui.tooling.preview)
      implementation(libs.material.icons)

      implementation(libs.androidx.lifecycle.viewmodel)
      implementation(libs.androidx.lifecycle.runtime.compose)
      implementation(libs.navigation.compose)

      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kermit.logging)

      implementation(libs.koin.core)
      implementation(libs.koin.compose)
      implementation(libs.koin.compose.viewmodel)

      implementation(libs.ktor.client.core)
      implementation(libs.ktor.client.content.negotiation)
      implementation(libs.ktor.serialization.kotlinx.json)

      implementation(libs.coil.compose)
      implementation(libs.coil.network.ktor3)
    }

    @Suppress("unused")
    val nonJsMain by getting {
      dependencies {
        implementation(libs.androidx.room.runtime)
        implementation(libs.sqlite.bundled)
      }
    }

    androidMain.dependencies {
      implementation(libs.androidx.activity.compose)
      implementation(libs.ktor.client.cio)
    }

    jvmMain.dependencies {
      implementation(compose.desktop.currentOs)
      implementation(libs.ktor.client.cio)
    }

    iosMain.dependencies { implementation(libs.ktor.client.darwin) }

    wasmJsMain.dependencies {
      implementation(libs.indexeddb)
      implementation(libs.ktor.client.js)
      // Satisfies the tslib@2 peer dependency memfs declares through webpack-dev-server.
      implementation(devNpm("tslib", "2.8.1"))
    }

    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotest.assertions)
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.ktor.client.mock)
      @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
      implementation(libs.compose.ui.test)
    }

    sourceSets.all {
      languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
      languageSettings.optIn("kotlin.time.ExperimentalTime")
    }
  }

  compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
}

ktfmt { googleStyle() }

tasks.withType<KtfmtBaseTask> { exclude("**/generated/**") }

room { schemaDirectory("$projectDir/schemas") }

dependencies {
  add("kspAndroid", libs.androidx.room.compiler)
  add("kspIosSimulatorArm64", libs.androidx.room.compiler)
  add("kspIosArm64", libs.androidx.room.compiler)
  add("kspJvm", libs.androidx.room.compiler)
}

// Skiko loads its native library through System::load, which JDK 24+ flags as restricted.
tasks.withType<Test>().configureEach { jvmArgs("--enable-native-access=ALL-UNNAMED") }

tasks.withType<KotlinWebpack>().configureEach {
  notCompatibleWithConfigurationCache("Kotlin/JS Webpack tasks store Project references")
}

tasks.withType<KotlinJsTest>().configureEach {
  notCompatibleWithConfigurationCache(
    "Kotlin/JS testing tasks store Project references and use non-serializable types."
  )
}

// Renames the fixed-name entry files to content-hashed names so an edge cache that ignores
// `Cache-Control: no-cache` cannot pair an old entry file with a new deploy's wasm.
// `HASHED_ASSET_PATTERN` in :server's StaticFrontendRoutes must keep matching these names.
val wasmJsProductionDist = layout.buildDirectory.dir("dist/wasmJs/productionExecutable")

val hashWasmJsBundle by tasks.registering {
  dependsOn("wasmJsBrowserDistribution")
  notCompatibleWithConfigurationCache(
    "reads and rewrites files wasmJsBrowserDistribution just wrote"
  )
  doLast {
    val distDir = wasmJsProductionDist.get().asFile

    fun hashEntryFile(fileName: String, htmlAttribute: String) {
      val asset = File(distDir, fileName)
      if (!asset.exists()) {
        error("Expected $asset to exist; did the wasmJs output filename change?")
      }
      val hash =
        MessageDigest.getInstance("SHA-256")
          .digest(asset.readBytes())
          .joinToString("") { "%02x".format(it) }
          .take(20)
      val hashedName =
        "${fileName.substringBeforeLast('.')}.$hash.${fileName.substringAfterLast('.')}"
      asset.copyTo(File(distDir, hashedName), overwrite = true)
      asset.delete()

      val indexHtml = File(distDir, "index.html")
      val original = indexHtml.readText()
      val rewritten =
        original.replace("$htmlAttribute=\"$fileName\"", "$htmlAttribute=\"$hashedName\"")
      check(rewritten != original) { "$indexHtml did not reference $fileName" }
      indexHtml.writeText(rewritten)
    }

    hashEntryFile("composeApp.js", htmlAttribute = "src")
    hashEntryFile("styles.css", htmlAttribute = "href")
  }
}

tasks.named("wasmJsBrowserDistribution") { finalizedBy(hashWasmJsBundle) }
