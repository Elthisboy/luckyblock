plugins {
    `kotlin-dsl`
    // Must track the Kotlin version embedded in the Gradle distribution (9.6.1 -> 2.3.21)
    kotlin("plugin.serialization") version "2.3.21"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.ajoberstar.grgit:grgit-core:5.3.3")
    implementation("com.charleskorn.kaml:kaml:0.104.0")
}
