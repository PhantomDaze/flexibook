plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.20.1-fabric"

// Chiseled multi-version build (SC 0.6 API)
stonecutter.registerChiseled(tasks.register("chiseledBuild", stonecutter.chiseled) {
    group = "project"
    ofTask("build")
})

// Each version node is auto-chiseled before direct tasks. Stonecutter writes the
// processed sources through the shared root src tree, so node generation must be
// ordered when both version tasks are requested in one Gradle invocation.
val versionPaths = listOf(":1.20.1", ":1.20.1-fabric")
subprojects {
    tasks.matching {
        it.name in setOf("compileJava", "compileTestJava", "classes", "processResources")
    }.configureEach {
        dependsOn("setupChiseledBuild")
    }
}

versionPaths.drop(1).forEach { path ->
    project(path).tasks.named("setupChiseledBuild").configure {
        mustRunAfter(
            ":1.20.1:compileJava",
            ":1.20.1:compileTestJava",
            ":1.20.1:classes",
            ":1.20.1:processResources"
        )
    }
}

// Sequential client smoke: Forge runClient → Fabric runClient.
// Enable with -Pflexibook.smokeTest=true (wired into each loader's client JVM props).
tasks.register("smokeTestAllClients") {
    group = "verification"
    description =
        "Run Forge then Fabric client smoke (create test world, open demo book ~10s, quit)."
    dependsOn(":1.20.1:runClient", ":1.20.1-fabric:runClient")
    // Strict order so only one client window/process runs at a time.
    project(":1.20.1-fabric").tasks.named("runClient").configure {
        mustRunAfter(":1.20.1:runClient")
    }
}
