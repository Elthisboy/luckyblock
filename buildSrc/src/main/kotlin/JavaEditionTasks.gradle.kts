package mod.lucky.build

val rootProjectProps = RootProjectProperties.fromProjectYaml(rootProject.rootDir)

val projectName = when(project.name) {
    "neoforge" -> ProjectName.LUCKY_BLOCK_NEOFORGE
    "fabric" -> ProjectName.LUCKY_BLOCK_FABRIC
    else -> throw Exception("Project name should be 'neoforge' or 'fabric'")
}
val projectProps = rootProjectProps.projects[projectName]!!

tasks.register<Copy>("copyRuntimeResources") {
    // The destination is the dev run directory, which also holds saves and logs.
    // Gradle 9 fingerprints the whole destination and fails on locked files such
    // as saves/<world>/session.lock, so opt out of state tracking here.
    doNotTrackState("copies into the dev run directory, which contains live game files")

    if (projectName == ProjectName.LUCKY_BLOCK_FABRIC) {
        into("$rootDir/fabric/run")
    } else {
        into("$rootDir/neoforge/run")
    }

    into("config/lucky/${projectProps.version}-${projectName.shortName}") {
        from("$rootDir/common/src/main/resources/lucky-config")
    }
    into("addons/lucky/custom-lucky-block") {
        from("$rootDir/common/src/main/resources/${ProjectName.CUSTOM_LUCKY_BLOCK_JAVA.fullName}")
    }
}

// Written into this project's own build dir: with both :fabric and :neoforge
// enabled, a shared output under common/build would make each project silently
// consume the other's zip (Gradle 9 rejects the implicit dependency outright).
tasks.register<Zip>("luckyBlockConfigDist") {
    archiveFileName.set("lucky-config.zip")
    destinationDirectory.set(layout.buildDirectory.dir("tmp"))
    from("$rootDir/common/src/main/resources/lucky-config")
}

tasks.register<Zip>("exportDist") {
    val distName = "${rootProject.name}-${projectName.shortName}-${projectProps.version}"
    val distDir = file("$rootDir/dist/$distName")
    destinationDirectory.set(distDir)
    archiveFileName.set("$distName.jar")

    doFirst {
        val distMeta = rootProjectProps.getDistMeta(rootDir, projectName)
        file(distDir).mkdirs()
        file("$distDir/meta.yaml").writeText(distMeta.toYaml())
    }
    val configDist = tasks.named<Zip>("luckyBlockConfigDist")

    from(zipTree("./build/libs/${rootProject.name}-${projectProps.version}.jar"))
    from(configDist.map { it.archiveFile }) { into("mod/lucky/java") }
    from("$rootDir/dist/$distName/meta.yaml")

    dependsOn(configDist)
    dependsOn(tasks.getByName("jar"))
}

tasks.register<Delete>("cleanDist") {
    delete("$rootDir/dist")
}
