import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    jacoco
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
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

    // --- Null-safety annotations (@NullMarked, @Nullable), same ones Spring 7 uses ---
    implementation(libs.jspecify)

    // --- Static analysis at compile time ---
    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

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

    options.errorprone {
        // Generated sources (annotation processors, later MapStruct) are not ours to fix.
        disableWarningsInGeneratedCode = true
        excludedPaths = ".*/build/generated/.*"
    }
}

// NullAway: production code only. Every package under com.dokiments is treated as
// non-null by default; anything that may be null must say so with @Nullable.
// Violations fail the build.
tasks.named<JavaCompile>("compileJava") {
    options.errorprone {
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "com.dokiments")
        option("NullAway:JSpecifyMode", "true")
    }
}

// Tests juggle mocks and deliberately-invalid input, where null checks are noise.
// Error Prone's other checks still run on them.
tasks.named<JavaCompile>("compileTestJava") {
    options.errorprone {
        check("NullAway", CheckSeverity.OFF)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

// --- Formatting -------------------------------------------------------------
// `./gradlew spotlessApply` fixes everything; `spotlessCheck` (part of `check`)
// fails the build — and CI — on unformatted code.
spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat(libs.versions.palantir.java.format.get()).formatJavadoc(true)
        removeUnusedImports()
        formatAnnotations()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
    format("misc") {
        target("*.md", ".gitignore", ".gitattributes", ".editorconfig", "**/*.yml", "**/*.yaml", "gradle/*.toml")
        targetExclude("build/**", ".gradle/**")
        trimTrailingWhitespace()
        leadingTabsToSpaces()
        endWithNewline()
    }
}

// --- Coverage ---------------------------------------------------------------
// Report only for now (build/reports/jacoco); a minimum threshold comes once
// there is real code to cover.
jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true // for CI / PR coverage tooling
        html.required = true // for humans
    }
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
