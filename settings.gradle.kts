pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
    }

    plugins {
        // Matches the Kotlin bundled by KotlinForForge 6.3.0 and fabric-language-kotlin 1.13.12
        kotlin("jvm") version "2.4.0"
    }
}

plugins {
    kotlin("jvm") apply false
    // Auto-provisions the JDK 25 toolchain required by Minecraft 26.1
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "lucky-block"
include("common")
include("tools")
include("neoforge")
include("fabric")
//include("bedrock")
