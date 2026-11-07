import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    alias(libs.plugins.spring.boot)
}

group = "com.dokiments"
version = versionFromGit()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Version alignment: Spring Boot's BOM (via Gradle's native platform support,
    // no dependency-management plugin needed) and Spring Modulith's BOM.
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(platform(libs.spring.modulith.bom))
    annotationProcessor(platform(SpringBootPlugin.BOM_COORDINATES))
    developmentOnly(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation(platform(libs.spring.modulith.bom))

    // --- Web / API ---
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)

    // --- Security & sessions ---
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.session.data.redis)

    // --- Operations ---
    implementation(libs.spring.boot.starter.actuator)

    // --- Architecture ---
    implementation(libs.spring.modulith.starter.core)

    // --- Firestore / Firebase ---
    implementation(libs.firebase.admin)

    // --- Configuration metadata for @ConfigurationProperties (IDE autocompletion) ---
    annotationProcessor(libs.spring.boot.configuration.processor)

    // --- Local development: starts compose.yaml services on bootRun ---
    developmentOnly(libs.spring.boot.docker.compose)

    // --- Tests ---
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.gcloud)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.archunit.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Keep method parameter names in bytecode (Spring uses them for binding).
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

springBoot {
    // Writes META-INF/build-info.properties, which Actuator's /actuator/info exposes
    // (version, build time). The Git commit is added in a later step.
    buildInfo()
}

/**
 * Derives the project version from the latest `v*` Git tag, so no version number is
 * ever committed:
 *
 * - on a release tag `v1.4.0`              → `1.4.0`
 * - 5 commits after it                      → `1.4.0-5-gabc1234`
 * - with uncommitted changes                → `…-dirty`
 * - no tag yet / no Git (e.g. source zip)   → `0.0.0-SNAPSHOT`
 *
 * CI must check out the full history (`fetch-depth: 0`), or tags are missing.
 */
fun versionFromGit(): String {
    val describe = providers.exec {
        commandLine("git", "describe", "--tags", "--match", "v[0-9]*", "--dirty")
        isIgnoreExitValue = true
    }

    return runCatching {
        if (describe.result.get().exitValue != 0) {
            null
        } else {
            describe.standardOutput.asText.get().trim().removePrefix("v").ifBlank { null }
        }
    }.getOrNull() ?: "0.0.0-SNAPSHOT"
}
