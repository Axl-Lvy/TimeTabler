import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.ktfmt)
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_21)
    optIn.add("kotlin.time.ExperimentalTime")
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
  testImplementation(projects.composeApp)
  testImplementation(testFixtures(projects.server))
  testImplementation(projects.server)
  testImplementation(projects.shared)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotest.assertions)
  testImplementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.ktor.client.core)
  testImplementation(libs.ktor.client.cio)
  testImplementation(libs.ktor.client.mock)
  testImplementation(libs.androidx.room.runtime)
  testImplementation(libs.sqlite.bundled)
  testRuntimeOnly(libs.slf4j.simple)
}

tasks.test {
  // Same Docker API floor as :server's tests.
  systemProperty("api.version", System.getenv("DOCKER_API_VERSION") ?: "1.40")
}

ktfmt { googleStyle() }
