import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.kotlinX.serialization.plugin)
  alias(libs.plugins.ktfmt)
  alias(libs.plugins.kover)
  application
  `java-test-fixtures`
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

application { mainClass.set("fr.axllvy.timetabler.server.MainKt") }

dependencies {
  implementation(projects.shared)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.krontab)
  implementation(libs.postgresql)
  implementation(libs.hikari)
  implementation(libs.jsoup)
  implementation(libs.playwright)
  implementation(libs.kermit.logging)
  implementation(libs.slf4j.api)
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.cio)
  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.netty)
  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.ktor.server.status.pages)
  implementation(libs.ktor.server.auto.head.response)
  implementation(libs.ktor.server.auth)
  implementation(libs.ktor.server.auth.jwt)
  implementation(libs.ktor.serialization.kotlinx.json)
  runtimeOnly(libs.slf4j.simple)

  // java-test-fixtures only sees main's api dependencies, so the fixtures declare their own.
  testFixturesImplementation(projects.shared)
  testFixturesImplementation(libs.kotlinx.coroutines.core)
  testFixturesImplementation(libs.hikari)
  testFixturesImplementation(libs.testcontainers.postgresql)
  testFixturesImplementation(libs.ktor.client.core)
  testFixturesImplementation(libs.ktor.server.core)
  testFixturesImplementation(libs.ktor.server.netty)

  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotest.assertions)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.ktor.client.mock)
  testImplementation(libs.ktor.server.test.host)
  testImplementation(libs.ktor.client.content.negotiation)
  // Testcontainers reports why a container failed to start only through slf4j.
  testRuntimeOnly(libs.slf4j.simple)
}

tasks.test {
  // Docker 29 refuses the API version Testcontainers negotiates by default; 1.40 is a safe floor.
  systemProperty("api.version", System.getenv("DOCKER_API_VERSION") ?: "1.40")
  // PlaywrightBrowserTest runs a real Chromium only when this is true.
  inputs.property("browserTests", providers.environmentVariable("TT_BROWSER_TESTS").orElse(""))
}

// Bakes the build sha into build-info.properties; the Docker build passes -PbuildSha=<sha7>.
tasks.processResources {
  val buildSha = providers.gradleProperty("buildSha").orElse("dev")
  inputs.property("buildSha", buildSha)
  filesMatching("build-info.properties") { expand("buildSha" to buildSha.get()) }
}

ktfmt { googleStyle() }

// Same engine as composeApp. Fixtures are test code, but Kover only excludes the test compilation.
kover {
  useJacoco("0.8.14")
  currentProject { sources { excludedSourceSets.add("testFixtures") } }
}
