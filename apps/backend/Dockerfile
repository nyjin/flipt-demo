# syntax=docker/dockerfile:1
#
# Multi-stage build for the Spring Boot backend.

# ---- Stage 1: build the executable jar with the Gradle wrapper (JDK 21) ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Resolve dependencies first for better layer caching.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

# ---- Stage 2: slim runtime (JRE 21) ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
