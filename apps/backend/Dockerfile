# syntax=docker/dockerfile:1
#
# Multi-stage build for the Spring Boot backend.

# ---- Stage 1: build the executable jar with the Gradle wrapper (JDK 25) ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Resolve dependencies first for better layer caching.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

# ---- Stage 2: slim runtime (JRE 25) ----
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8081
# --enable-native-access: the Flipt in-memory SDK loads a native engine via JNA;
# granting native access silences the JDK 25 restricted-method warning (and is
# required once the JDK starts blocking it by default).
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/app/app.jar"]
