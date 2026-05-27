plugins {
    // Auto-provisions the Java 26 toolchain (downloads it if missing) so the
    // build works regardless of which JDK is installed locally / on CI.
    // 1.0.0+ is required for Gradle 9 (drops the removed JvmVendorSpec.IBM_SEMERU).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "flipt-demo-backend"
