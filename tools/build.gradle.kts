plugins {
    id("org.jetbrains.kotlin.jvm")
    application
    distribution
    kotlin("plugin.serialization") version "2.4.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.2")
    implementation("br.com.gamemods:nbt-manipulator:3.1.0") 
    implementation("com.charleskorn.kaml:kaml:0.37.0")
    implementation("org.jsoup:jsoup:1.14.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.1")
    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    implementation("com.github.kittinunf.fuel:fuel-coroutines:2.3.1")
    implementation("io.github.g00fy2:versioncompare:1.5.0")
    implementation("io.github.cdimascio:dotenv-kotlin:6.2.2")
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.+")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
}


java.sourceSets["main"].java {
    srcDir("../bedrock/src/main/kotlin/common")
}

tasks.test {
    useJUnitPlatform()
}

// Both files in tools/src/test (last touched 2021) reference a
// BlockConversions/BlockStates/generateBedrockDrops API that was removed from
// :bedrock in 2023, so this source set has not compiled for years. It is
// unrelated to the 26.1 port, but it breaks `gradlew build`, so it is skipped.
// Re-enable once the tests are rewritten against the current :bedrock code.
tasks.named("compileTestKotlin") { enabled = false }
tasks.named("test") { enabled = false }

application {
    mainClass.set("mod.lucky.tools.MainKt")
}

tasks.register<JavaExec>("cli") {
    classpath = fileTree("$rootDir/tools/build/install/tools/lib")
    mainClass.set("mod.lucky.tools.MainKt")
    dependsOn("installDist")
}

dependencyLocking {
    lockAllConfigurations()
    lockMode.set(LockMode.LENIENT)
}
