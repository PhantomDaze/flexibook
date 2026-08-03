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

// Shared smoke inputs live at the monorepo root so Forge + Fabric (and future
// version nodes) all see the same tree under run/:
//
//   dev/smoke/resourcepacks/<pack_folder_or.zip>  → run/resourcepacks/
//   dev/smoke/book.txt                            → run/book.txt
//
// On smoke (-Pflexibook.smokeTest=true):
//   - every file/* pack under run/resourcepacks/ is force-enabled
//   - book id is the first non-# line of run/book.txt (blank → built-in demo)
val sharedSmokePacksDir = rootProject.layout.projectDirectory.dir("dev/smoke/resourcepacks")
val sharedSmokeBookFile = rootProject.layout.projectDirectory.file("dev/smoke/book.txt")

versionPaths.forEach { path ->
    val node = project(path)
    node.afterEvaluate {
        val syncPacks = node.tasks.register("syncSmokeResourcePacks", Copy::class.java) {
            group = "verification"
            description =
                "Copy shared dev/smoke/resourcepacks into this node's run/resourcepacks/"
            from(sharedSmokePacksDir) {
                exclude("README.md", "**/.gitkeep")
            }
            into(node.layout.projectDirectory.dir("run/resourcepacks"))
            // Do not wipe unrelated packs the user dropped into run/resourcepacks.
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
            onlyIf { sharedSmokePacksDir.asFile.isDirectory }
        }
        val syncBook = node.tasks.register("syncSmokeBookId", Copy::class.java) {
            group = "verification"
            description = "Copy shared dev/smoke/book.txt into this node's run/book.txt"
            from(sharedSmokeBookFile)
            into(node.layout.projectDirectory.dir("run"))
            rename { "book.txt" }
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
            onlyIf { sharedSmokeBookFile.asFile.isFile }
        }
        node.tasks.matching { it.name == "runClient" }.configureEach {
            dependsOn(syncPacks, syncBook)
        }
    }
}

// Sequential client smoke: Forge runClient → Fabric runClient.
// Enable with -Pflexibook.smokeTest=true (wired into each loader's client JVM props).
tasks.register("smokeTestAllClients") {
    group = "verification"
    description =
        "Run Forge then Fabric client smoke (auto packs + book.txt id, quit)."
    dependsOn(":1.20.1:runClient", ":1.20.1-fabric:runClient")
    // Strict order so only one client window/process runs at a time.
    project(":1.20.1-fabric").tasks.named("runClient").configure {
        mustRunAfter(":1.20.1:runClient")
    }
}
