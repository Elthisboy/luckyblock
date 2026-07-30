#
# Base
#
# Minecraft 26.1 requires Java 25, and Fabric Loom requires the Gradle daemon
# itself to run on it (see gradle/gradle-daemon-jvm.properties).
FROM eclipse-temurin:25-jdk AS base

RUN apt-get update && apt-get install -y git

# setup gradle
WORKDIR /app

COPY ./gradlew ./gradlew
COPY ./gradle ./gradle

RUN ./gradlew -v

COPY ./settings.gradle.kts ./gradle.properties ./project.yaml ./
COPY ./buildSrc ./buildSrc
COPY ./common/build.gradle.kts ./common/build.gradle.kts
COPY ./tools/build.gradle.kts ./tools/build.gradle.kts
COPY ./neoforge/build.gradle.kts ./neoforge/build.gradle.kts
COPY ./fabric/build.gradle.kts ./fabric/build.gradle.kts
COPY ./bedrock/build.gradle.kts ./bedrock/build.gradle.kts

RUN ./gradlew dependencies --info

#
# Test
#
FROM base AS test

WORKDIR /app
COPY . .

# :common is a plain Kotlin/JVM module, so the task is `test`, not `jvmTest`
RUN ./gradlew :common:test --info
RUN ./gradlew :tools:test --info

#
# Build
#
FROM base AS build

WORKDIR /app
COPY . .
RUN ./gradlew :neoforge:build
RUN ./gradlew :fabric:build
#RUN ./gradlew :bedrock:build
