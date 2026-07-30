import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import mod.lucky.build.*

val rootProjectProps = RootProjectProperties.fromProjectYaml(rootProject.rootDir)
val projectProps = rootProjectProps.projects[ProjectName.LUCKY_BLOCK_FABRIC]!!

plugins {
    kotlin("jvm")
    id("mod.lucky.build.JavaEditionTasks")
    // https://maven.fabricmc.net/net/fabricmc/fabric-loom/
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
}

repositories {
    mavenCentral()
}

base.archivesName = rootProject.name
version = projectProps.version

dependencies {
    implementation(project(":common"))

    // Minecraft 26.1 ships deobfuscated: Mojang no longer publishes ProGuard
    // mappings and neither Yarn nor intermediary exist for 26.x. Loom therefore
    // runs in its no-remap mode -- there is no `mappings` dependency, and mod
    // dependencies are plain `implementation` rather than `modImplementation`.
    minecraft("com.mojang:minecraft:${projectProps.lockedDependencies["minecraft"]!!}")

    implementation("net.fabricmc:fabric-loader:${projectProps.lockedDependencies["fabric-loader"]!!}")
    implementation("net.fabricmc.fabric-api:fabric-api:${projectProps.lockedDependencies["fabric-api"]!!}")

    // The Kotlin runtime is provided at runtime by the fabric-language-kotlin
    // mod instead of being shadowed into our jar.
    implementation("net.fabricmc:fabric-language-kotlin:${projectProps.lockedDependencies["fabric-language-kotlin"]!!}")
}

tasks.processResources {
    from("../common/src/main/resources/game")
    from("src/main/generated")
    inputs.property("modVersion", projectProps.version)
    filesMatching("fabric.mod.json") {
        expand(
            "modVersion" to projectProps.version,
            "minMinecraftVersion" to projectProps.dependencies["minecraft"]!!.minInclusive!!,
            "minFabricLoaderVersion" to projectProps.dependencies["fabric-loader"]!!.minInclusive!!,
        )
    }
    dependsOn(tasks.getByName("copyRuntimeResources"))
}

tasks.jar {
    archiveBaseName.set(rootProject.name)
    // :common is a plain Kotlin module, so fold its classes into the mod jar.
    from(project(":common").sourceSets.main.get().output)
}

tasks.assemble { dependsOn(tasks.getByName("exportDist").mustRunAfter(tasks.jar)) }
tasks.getByName("runClient").dependsOn(tasks.getByName("copyRuntimeResources"))
tasks.getByName("runServer").dependsOn(tasks.getByName("copyRuntimeResources"))

val javaVersion = projectProps.dependencies["java"]!!.maxInclusive!!
java.toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion.toInt()))

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(javaVersion))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaVersion.toInt())
}
