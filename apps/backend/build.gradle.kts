plugins {
    java
    id("org.springframework.boot") version "3.5.14"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}

repositories {
    mavenCentral()
    // The official GrowthBook Java SDK is published on JitPack, not Maven Central.
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // OpenFeature SDK + OFREP provider (speaks Flipt v2's OFREP endpoint).
    implementation("dev.openfeature:sdk:1.20.2")
    implementation("dev.openfeature.contrib.providers:ofrep:0.0.1")

    // Flipt official client-side SDK (Rust engine via JNA/FFI) — performs
    // in-process, in-memory evaluation after fetching a snapshot from Flipt.
    // Used by the default `flipt.mode=in-memory` path (wrapped as an OpenFeature
    // provider); `flipt.mode=ofrep` falls back to the OFREP provider above.
    implementation("io.flipt:flipt-client-java:1.3.1")

    // GrowthBook native Java SDK (multi-user GrowthBookClient, 0.9.0+) — used by
    // the /api/growthbook/* endpoints to compare against Flipt.
    implementation("com.github.growthbook:growthbook-sdk-java:0.10.6")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
