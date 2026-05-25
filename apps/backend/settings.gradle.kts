plugins {
    // Auto-provisions the Java 21 toolchain (downloads it if missing) so the
    // build works regardless of which JDK is installed locally / on CI.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

rootProject.name = "flipt-demo-backend"
