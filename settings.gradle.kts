plugins {
    // Lets Gradle download the Java toolchain declared in build.gradle.kts when the
    // machine running the build doesn't have it installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "dokiments-api"
