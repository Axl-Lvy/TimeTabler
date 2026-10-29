import com.ncorti.ktfmt.gradle.tasks.KtfmtCheckTask
import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.ktfmt)
}

android {
  namespace = "fr.axllvy.timetabler"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "fr.axllvy.timetabler"
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.targetSdk.get().toInt()
    versionCode = 1
    versionName = "0.0.1"
  }

  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

  buildTypes { getByName("release") { isMinifyEnabled = false } }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }

ktfmt { googleStyle() }

// The ktfmt plugin does not detect source sets compiled by AGP 9's built-in Kotlin support.
val ktfmtFormatAndroidSources =
  tasks.register<KtfmtFormatTask>("ktfmtFormatAndroidSources") {
    source = fileTree("src") { include("**/*.kt") }
  }

val ktfmtCheckAndroidSources =
  tasks.register<KtfmtCheckTask>("ktfmtCheckAndroidSources") {
    source = fileTree("src") { include("**/*.kt") }
  }

tasks.named("ktfmtFormat") { dependsOn(ktfmtFormatAndroidSources) }

tasks.named("ktfmtCheck") { dependsOn(ktfmtCheckAndroidSources) }

dependencies {
  implementation(project(":composeApp"))
  implementation(libs.compose.runtime)
  implementation(libs.compose.foundation)
  implementation(libs.compose.ui)
  implementation(libs.androidx.activity.compose)
}
