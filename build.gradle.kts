import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.springframework.boot.gradle.plugin.SpringBootPlugin

// Security override for the build's own tooling, not the application: the Spring Boot Gradle
// plugin brings in commons-lang3 3.16.0, which has CVE-2025-48924 (fixed in 3.18.0). It only runs
// inside Gradle while building, but there's no reason to keep a known-vulnerable version.
// Remove once the plugin ships a fixed version.
buildscript {
    dependencies {
        constraints {
            classpath(libs.commons.lang3) {
                because("CVE-2025-48924: uncontrolled recursion in versions before 3.18.0")
            }
        }
    }
}

plugins {
    java
    jacoco
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
}

group = "com.dokiments"

// Version and commit normally come from Git. Builds without a .git directory, such as
// the Docker build, pass them in instead with `-PappVersion=...` and `-PgitCommit=...`
// (see the Dockerfile). A blank value falls back to Git.
version = providers.gradleProperty("appVersion").orNull?.ifBlank { null } ?: versionFromGit()
val buildCommit = providers.gradleProperty("gitCommit").orNull?.ifBlank { null } ?: gitCommit()

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

    // Security overrides (versions in gradle/libs.versions.toml). Gradle uses the highest version
    // any BOM or constraint asks for, so these win over the vulnerable versions selected by the
    // Spring Boot BOM (OpenTelemetry, CVE-2026-118259) and the Firebase and Google libraries
    // (Guava, CVE-2026-102554).
    // OpenTelemetry is imported as a BOM so all its modules move together.
    implementation(platform(libs.opentelemetry.bom))
    constraints {
        implementation(libs.guava) {
            because("CVE-2026-102554: denial of service in versions before 33.7.2")
        }
    }
    annotationProcessor(platform(SpringBootPlugin.BOM_COORDINATES))
    developmentOnly(platform(SpringBootPlugin.BOM_COORDINATES))

    // Web / API
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    // Describes the API (OpenAPI) and serves Swagger UI, in the dev profile only
    implementation(libs.springdoc.openapi.webmvc.ui)

    // Calls to external services (Firebase Auth, Google, Resend)
    implementation(libs.spring.boot.starter.restclient)

    // Email templates. Only Thymeleaf's engine, not the web starter: the API renders no web pages.
    implementation(libs.thymeleaf.spring6)

    // Security & sessions
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.session.data.redis)

    // Operations
    implementation(libs.spring.boot.starter.actuator)
    // Traces and metrics, exported with OTLP to whichever collector the platform provides
    implementation(libs.spring.boot.starter.opentelemetry)

    // Architecture
    implementation(libs.spring.modulith.starter.core)

    // Firestore / Firebase
    implementation(libs.firebase.admin)

    // Configuration metadata for @ConfigurationProperties (IDE autocompletion)
    annotationProcessor(libs.spring.boot.configuration.processor)

    // Null-safety annotations (@NullMarked, @Nullable), same ones Spring 7 uses
    implementation(libs.jspecify)

    // Static analysis at compile time
    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    // Local development: starts compose.yaml services on bootRun
    developmentOnly(libs.spring.boot.docker.compose)

    // Restarts the application automatically when compiled classes change during bootRun.
    // developmentOnly: never part of the jar, the Docker image or the tests.
    developmentOnly(libs.spring.boot.devtools)

    // Tests
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.archunit.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Keep method parameter names in bytecode (Spring uses them for binding).
    options.compilerArgs.add("-parameters")

    // Every compiler and Error Prone warning fails the build, so problems are fixed while the
    // code is being written instead of piling up. `processing` is left out because annotation
    // processors such as the configuration processor warn about things that aren't ours.
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing", "-Werror"))

    options.errorprone {
        // Error Prone reports these as warnings by default; -Werror makes them fail the build.
        // Unused code is usually a leftover or a mistake (a parameter that should have been used).
        check("UnusedVariable", CheckSeverity.ERROR)
        check("UnusedMethod", CheckSeverity.ERROR)
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

    // Print why a test failed (assertion message, causes and a trimmed stack trace)
    // directly in the terminal, instead of only "FAILED" and a pointer to the HTML report.
    testLogging {
        events(TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }

    // One summary line per test task, e.g. "integrationTest: 11 passed, 0 failed, 0 skipped".
    val taskName = name
    addTestListener(
        object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) {}

            override fun beforeTest(testDescriptor: TestDescriptor) {}

            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}

            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                if (suite.parent == null) {
                    println(
                        "$taskName: ${result.successfulTestCount} passed, ${result.failedTestCount} failed, " +
                            "${result.skippedTestCount} skipped (${result.resultType})",
                    )
                }
            }
        },
    )
}

// Tests: fast vs integration
// `./gradlew test`             unit + architecture tests, no Docker needed (seconds).
// `./gradlew integrationTest`  tests tagged "integration" (@IntegrationTest), real
//                              containers via Testcontainers; Docker must be running.
// `./gradlew check` / `build`  runs both.
val integrationTag = "integration"

tasks.test {
    useJUnitPlatform {
        excludeTags(integrationTag)
    }
}

val integrationTest by tasks.registering(Test::class) {
    description = "Runs integration tests against real infrastructure (Testcontainers)."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags(integrationTag)
    }
    shouldRunAfter(tasks.test)
    // OpenApiSpecIT compares the API with this file, so editing it must re-run the tests.
    inputs.file("docs/openapi.yaml")
    // Registered further down, so referred to by name.
    dependsOn("buildFirebaseEmulatorsImage")
}

// The Docker command line. An IDE started from the desktop can give Gradle a PATH without the
// folder Docker is installed in, so its usual install locations are tried as well.
fun dockerCommand(): String {
    val onPath =
        System
            .getenv("PATH")
            .orEmpty()
            .split(File.pathSeparator)
            .map { File(it, "docker") }
    val usual =
        listOf(
            "/usr/local/bin/docker",
            "/opt/homebrew/bin/docker",
            "${System.getProperty("user.home")}/.docker/bin/docker",
            "/Applications/Docker.app/Contents/Resources/bin/docker",
            "/usr/bin/docker",
        ).map(::File)
    return (onPath + usual).firstOrNull { it.canExecute() }?.path ?: "docker"
}

// The Firebase emulators image the integration tests run (see docker/firebase-emulators and
// TestcontainersConfiguration), built under the same name compose.yaml uses. Docker's build cache
// makes this take a second or two when nothing in that folder has changed.
//
// On a network with a TLS-inspecting proxy, set EXTRA_CA_PEM to the proxy's root certificate
// (as for `docker compose --profile full up --build`): it is passed to the build as a secret.
val buildFirebaseEmulatorsImage by tasks.registering(Exec::class) {
    description = "Builds the Firebase emulators image used by the integration tests."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    val extraCa = providers.environmentVariable("EXTRA_CA_PEM").orNull?.ifBlank { null }
    commandLine(
        buildList {
            addAll(listOf(dockerCommand(), "build", "--quiet", "--tag", "dokiments-firebase-emulators:local"))
            if (extraCa != null) addAll(listOf("--secret", "id=extra_ca,env=EXTRA_CA_PEM"))
            add("docker/firebase-emulators")
        },
    )
    // Always handed to Docker, which knows whether anything changed.
    outputs.upToDateWhen { false }
}

// `./gradlew updateOpenApi` rewrites docs/openapi.yaml from the code, after a deliberate change to the
// API. OpenApiSpecIT then passes again, and the change to the file shows the new contract in review.
val updateOpenApi by tasks.registering(Test::class) {
    description = "Rewrites docs/openapi.yaml from the application's API description."
    group = "documentation"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags(integrationTag)
    }
    filter {
        includeTestsMatching("com.dokiments.platform.apidocs.OpenApiSpecIT")
    }
    systemProperty("openapi.update", "true")
    dependsOn(buildFirebaseEmulatorsImage)
    // Always runs: its job is writing the file, not checking it.
    outputs.upToDateWhen { false }
}

tasks.check {
    dependsOn(integrationTest)
}

// Formatting
// `./gradlew spotlessApply` fixes everything; `spotlessCheck` (part of `check`)
// fails the build, and therefore CI, on unformatted code.
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
        // IntelliJ's Kotlin style rather than ktlint's default. Set here, not in .editorconfig,
        // because the key is ktlint-only and editors flag it as unknown there.
        ktlint().editorConfigOverride(mapOf("ktlint_code_style" to "intellij_idea"))
    }
    format("misc") {
        target("*.md", ".gitignore", ".gitattributes", ".editorconfig", "**/*.yml", "**/*.yaml", "gradle/*.toml")
        targetExclude("build/**", ".gradle/**")
        trimTrailingWhitespace()
        leadingTabsToSpaces()
        endWithNewline()
    }
}

// Coverage
// Report only for now (build/reports/jacoco); a minimum threshold comes once
// there is real code to cover.
jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.jacocoTestReport {
    // Combines whichever suites have run (test.exec, integrationTest.exec). Reading the
    // files rather than the tasks matters: depending on the tasks would make a plain
    // `./gradlew test` run the integration tests too.
    executionData.setFrom(fileTree(layout.buildDirectory.dir("jacoco")) { include("*.exec") })
    mustRunAfter(tasks.test, integrationTest)
    reports {
        xml.required = true // for CI / PR coverage tooling
        html.required = true // for humans
    }
}

springBoot {
    // Writes META-INF/build-info.properties, which /actuator/info exposes as
    // `build.version`, `build.time` and `build.commit`.
    buildInfo {
        properties {
            additional = mapOf("commit" to buildCommit)
        }
    }
}

// `./gradlew bootRun` is local development: use the `dev` profile unless one is set
// explicitly (SPRING_PROFILES_ACTIVE). Deployed containers set their own profile.
tasks.bootRun {
    if (System.getenv("SPRING_PROFILES_ACTIVE").isNullOrBlank()) {
        systemProperty("spring.profiles.active", "dev")
    }
}

// Prints the version worked out above, so CI can tag and label the Docker image with
// exactly the version Gradle uses, without repeating the Git logic in a shell script.
// Usage: ./gradlew -q printVersion
tasks.register("printVersion") {
    description = "Prints the version Gradle works out from Git tags."
    group = "help"
    val projectVersion = version.toString()
    doLast {
        println(projectVersion)
    }
}

/**
 * Derives the project version from the latest `v*` Git tag, so no version number is
 * ever committed:
 *
 * - on a release tag `v1.4.0`, the version is `1.4.0`
 * - 5 commits after it, it is `1.4.0-5-gabc1234`
 * - with uncommitted changes, `-dirty` is appended
 * - with no tag yet, or no Git at all (e.g. a source zip), it is `0.0.0-SNAPSHOT`
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

/** Short hash of the commit being built, or `unknown` outside a Git checkout. */
fun gitCommit(): String {
    val revParse = providers.exec {
        commandLine("git", "rev-parse", "--short=12", "HEAD")
        isIgnoreExitValue = true
    }

    return runCatching {
        if (revParse.result.get().exitValue != 0) {
            null
        } else {
            revParse.standardOutput.asText.get().trim().ifBlank { null }
        }
    }.getOrNull() ?: "unknown"
}
