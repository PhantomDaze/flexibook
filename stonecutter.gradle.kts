plugins {
    id("dev.kikugie.stonecutter")
}

// ---------------------------------------------------------------------------
// Node sets
//
// One root controller serves BOTH builds: the main settings (Gradle 9.6.1,
// 9 nodes) and the legacy settings (Gradle 8.14.3, 1.20.1 Forge/Fabric).
// Tasks are only registered when their node projects exist in the current
// settings file, so both invocations configure cleanly.
//
// Pass L — legacy build (JDK21 daemon, Gradle 8.14.3 wrapper)
// Pass A — main build, Java 21 nodes (JDK21 daemon)
// Pass B — main build, Java 25 nodes (JDK25 daemon)
// ---------------------------------------------------------------------------
val legacyNodes = listOf("1.20.1", "1.20.1-fabric")
val passANodes = listOf(
    "1.21.1", "1.21.1-fabric", "1.21.4", "1.21.4-fabric", "1.21.11-fabric"
)
val passBNodes = listOf("26.1.2", "26.1.2-fabric", "26.2", "26.2-fabric")
val allNodes = legacyNodes + passANodes + passBNodes

fun hasNode(project: org.gradle.api.Project, node: String): Boolean =
    project.rootProject.findProject(":$node") != null

// Default IDE/configuration node only; build tasks preprocess each node independently.
// The legacy settings have no 26.x node, so fall back to its fabric node there.
val activeNode = providers.gradleProperty("stonecutter.active").orNull
    ?: if (hasNode(project, "26.1.2")) "26.1.2" else "1.20.1-fabric"
stonecutter active activeNode

// Chiseled multi-version build (SC 0.6 API).
// NOTE: chiseledBuild visits every node in ONE Gradle daemon. That is fine for the
// configuration/compile graph, but the 26.x nodes need a JDK25 daemon while
// 1.20.1/1.21.x need JDK21 — use scripts/build-all.sh for real two-pass builds.
stonecutter.registerChiseled(tasks.register("chiseledBuild", stonecutter.chiseled) {
    group = "project"
    ofTask("build")
})

// ---------------------------------------------------------------------------
// Build pass helpers (JVM selection still comes from JAVA_HOME)
// ---------------------------------------------------------------------------
val legacyInThisBuild = legacyNodes.any { hasNode(project, it) }
val mainInThisBuild = passANodes.any { hasNode(project, it) } || passBNodes.any { hasNode(project, it) }

if (passANodes.all { hasNode(project, it) }) {
    tasks.register("buildPassA") {
        group = "project"
        description = "Build the 5 Java 21 nodes (run with JAVA_HOME=<jdk21>; 1.20.1 lives in legacy-gradle8)"
        dependsOn(passANodes.map { ":$it:build" })
    }
}
if (passBNodes.all { hasNode(project, it) }) {
    tasks.register("buildPassB") {
        group = "project"
        description = "Build the 4 Java 25 nodes (run with JAVA_HOME=<jdk25>)"
        dependsOn(passBNodes.map { ":$it:build" })
    }
}

// ---------------------------------------------------------------------------
// Smoke orchestration
//
// Every pass runs in its own Gradle invocation because Gradle picks the daemon
// JVM from JAVA_HOME: Pass L must use the 8.14.3 wrapper, Pass B needs JDK25.
// The child processes inherit stdio so the client logs stay visible.
// ---------------------------------------------------------------------------
fun smokeJdk(project: org.gradle.api.Project, prop: String, env: String, fallback: String): String =
    project.providers.gradleProperty(prop).orNull
        ?: System.getenv(env)
        ?: fallback

val smokeJdk21 = smokeJdk(project, "smoke.jdk21", "JDK21", "/usr/lib/jvm/java-21-openjdk")
val smokeJdk25 = smokeJdk(project, "smoke.jdk25", "JDK25", "/usr/lib/jvm/java-25-openjdk")

fun smokeReadSeconds(project: org.gradle.api.Project): String =
    project.providers.gradleProperty("flexibook.autoSmoke.readSeconds").orNull
        ?: project.providers.gradleProperty("flexibook.smokeTest.readSeconds").orNull
        ?: "10"

fun smokeTimeoutSeconds(project: org.gradle.api.Project): String =
    project.providers.gradleProperty("flexibook.autoSmoke.timeoutSeconds").orNull
        ?: project.providers.gradleProperty("flexibook.smokeTest.timeoutSeconds").orNull
        ?: "300"

fun smokeWrapper(project: org.gradle.api.Project, legacy: Boolean): File {
    val windows = org.gradle.internal.os.OperatingSystem.current().isWindows
    val name = if (windows) "gradlew.bat" else "gradlew"
    val base = if (legacy) project.rootDir.resolve("legacy-gradle8") else project.rootDir
    return base.resolve(name)
}

/** Runs one node's client smoke in a child Gradle invocation with an explicit JAVA_HOME. */
fun runSmokeGradle(
    project: org.gradle.api.Project,
    legacy: Boolean,
    node: String,
    javaHome: String
) {
    val read = smokeReadSeconds(project)
    val timeout = smokeTimeoutSeconds(project)
    val args = if (legacy) {
        listOf(
            "-c", "settings.legacy.gradle.kts",
            "-Pstonecutter.active=1.20.1-fabric",
            "-Pflexibook.autoSmoke=true",
            "-Pflexibook.autoSmoke.readSeconds=$read",
            "-Pflexibook.autoSmoke.timeoutSeconds=$timeout",
            ":$node:runClient",
            "--console=plain"
        )
    } else {
        listOf(
            "-Pflexibook.autoSmoke.readSeconds=$read",
            "-Pflexibook.autoSmoke.timeoutSeconds=$timeout",
            ":$node:runClientAutoSmoke",
            "--console=plain"
        )
    }

    val wrapper = smokeWrapper(project, legacy)
    val windows = org.gradle.internal.os.OperatingSystem.current().isWindows
    val command = if (windows) {
        listOf("cmd", "/c", wrapper.absolutePath) + args
    } else {
        listOf(wrapper.absolutePath) + args
    }

    val label = if (legacy) "smoke [$node, legacy-gradle8, JAVA_HOME=$javaHome]"
                else "smoke [$node, JAVA_HOME=$javaHome]"
    project.logger.lifecycle("")
    project.logger.lifecycle("========== $label ==========")
    val pb = ProcessBuilder(command)
    pb.directory(project.rootDir)
    pb.inheritIO()
    pb.environment()["JAVA_HOME"] = javaHome
    val code = pb.start().waitFor()
    if (code != 0) {
        throw GradleException("$label failed with exit code $code")
    }
    project.logger.lifecycle("========== finished $label ==========")
}

fun registerSmokeSequence(
    project: org.gradle.api.Project,
    taskName: String,
    description: String,
    legacy: Boolean,
    nodes: List<String>,
    javaHome: String
) {
    project.tasks.register(taskName) {
        group = "verification"
        this.description = description
        doLast {
            nodes.forEach { node -> runSmokeGradle(project, legacy, node, javaHome) }
            project.logger.lifecycle("")
            project.logger.lifecycle("$taskName: all nodes completed")
        }
    }
}

// Pass L — legacy 1.20.1 nodes.
// Inside the legacy build itself, run the two runClient tasks directly (no
// nested Gradle). The legacy loader scripts turn smoke on from the task name.
// From the main build, spawn the 8.14.3 wrapper with -Pflexibook.autoSmoke=true.
if (legacyInThisBuild) {
    tasks.register("smokeAllClientsPassL") {
        group = "verification"
        description = "Run the 1.20.1 Forge/Fabric client smoke (legacy-gradle8, JDK21), sequentially"
        dependsOn(":1.20.1:runClient", ":1.20.1-fabric:runClient")
        project(":1.20.1-fabric").tasks.named("runClient").configure {
            mustRunAfter(":1.20.1:runClient")
        }
    }
} else {
    registerSmokeSequence(
        project,
        "smokeAllClientsPassL",
        "Run the 1.20.1 Forge/Fabric client smoke through legacy-gradle8 (JDK21), sequentially",
        legacy = true,
        nodes = legacyNodes,
        javaHome = smokeJdk21
    )
}

// Pass A — main build, Java 21 nodes.
if (passANodes.all { hasNode(project, it) }) {
    registerSmokeSequence(
        project,
        "smokeAllClientsPassA",
        "Run clientAutoSmoke on the 5 Java 21 nodes (JDK21), sequentially",
        legacy = false,
        nodes = passANodes,
        javaHome = smokeJdk21
    )
}

// Pass B — main build, Java 25 nodes.
if (passBNodes.all { hasNode(project, it) }) {
    registerSmokeSequence(
        project,
        "smokeAllClientsPassB",
        "Run clientAutoSmoke on the 4 Java 25 nodes (JDK25), sequentially",
        legacy = false,
        nodes = passBNodes,
        javaHome = smokeJdk25
    )
}

/**
 * One command for all 11 nodes: Pass L → Pass A → Pass B.
 * In the main build every node runs in a child Gradle invocation with its own
 * JAVA_HOME. In the legacy build only Pass L exists and runs in-process.
 */
if (mainInThisBuild) {
    tasks.register("smokeAllClients") {
        group = "verification"
        description = "Run the client smoke on all 11 nodes: legacy 1.20.1 (JDK21) → 1.21.x (JDK21) → 26.x (JDK25)"
        doLast {
            legacyNodes.forEach { node -> runSmokeGradle(project, true, node, smokeJdk21) }
            passANodes.forEach { node -> runSmokeGradle(project, false, node, smokeJdk21) }
            passBNodes.forEach { node -> runSmokeGradle(project, false, node, smokeJdk25) }
            project.logger.lifecycle("")
            project.logger.lifecycle("smokeAllClients: all 11 nodes completed")
        }
    }
} else {
    tasks.register("smokeAllClients") {
        group = "verification"
        description = "Run the client smoke on the legacy 1.20.1 nodes"
        dependsOn("smokeAllClientsPassL")
    }
}

// ---------------------------------------------------------------------------
// Backwards-compatible aliases (existing .run/*.xml configs keep working)
// ---------------------------------------------------------------------------
if (passBNodes.all { hasNode(project, it) }) {
    // main: 26.1.2, 26.2, 26.1.2-fabric, 26.2-fabric (original order/semantics)
    registerSmokeSequence(
        project,
        "autoSmokeClients",
        "Alias: clientAutoSmoke on the 4 NeoForge/Fabric 26.x nodes (JDK25)",
        legacy = false,
        nodes = listOf("26.1.2", "26.2", "26.1.2-fabric", "26.2-fabric"),
        javaHome = smokeJdk25
    )
}
if (passANodes.all { hasNode(project, it) }) {
    // java21: the 5 Java 21 nodes
    registerSmokeSequence(
        project,
        "autoSmokeAllClients",
        "Alias: clientAutoSmoke on the 5 Java 21 nodes (JDK21)",
        legacy = false,
        nodes = passANodes,
        javaHome = smokeJdk21
    )
}
if (legacyInThisBuild) {
    // 1.20.1: Forge then Fabric runClient (smoke enabled by the task name)
    tasks.register("smokeTestAllClients") {
        group = "verification"
        description = "Alias: Forge then Fabric 1.20.1 client smoke (runClient, JDK21)"
        dependsOn("smokeAllClientsPassL")
    }
} else {
    registerSmokeSequence(
        project,
        "smokeTestAllClients",
        "Alias: Forge then Fabric 1.20.1 client smoke through legacy-gradle8 (JDK21)",
        legacy = true,
        nodes = legacyNodes,
        javaHome = smokeJdk21
    )
}

// ---------------------------------------------------------------------------
// Shared smoke fixtures: dev/smoke → versions/<node>/run/
//
//   dev/smoke/resourcepacks/<pack_folder_or.zip>  → run/resourcepacks/
//   dev/smoke/book.txt                            → run/book.txt
//
// Every node (both builds) gets the same pair of Copy tasks. runClient and
// runClientAutoSmoke depend on them, so direct IDE/client runs also see the
// fixtures. The mod additionally reads dev/smoke directly via system props.
// ---------------------------------------------------------------------------
val sharedSmokePacksDir = rootProject.layout.projectDirectory.dir("dev/smoke/resourcepacks")
val sharedSmokeBookFile = rootProject.layout.projectDirectory.file("dev/smoke/book.txt")

val smokeSyncNodes = allNodes.filter { hasNode(project, it) }
smokeSyncNodes.forEach { nodeName ->
    val nodeProject = rootProject.project(":$nodeName")
    nodeProject.afterEvaluate {
        val syncPacks = nodeProject.tasks.register("syncSmokeResourcePacks", Copy::class.java) {
            group = "verification"
            description = "Copy shared dev/smoke/resourcepacks into :$nodeName's run/resourcepacks/"
            from(sharedSmokePacksDir) {
                exclude("README.md", "README.txt", "**/.gitkeep")
            }
            into(nodeProject.layout.projectDirectory.dir("run/resourcepacks"))
            // Do not wipe unrelated packs the user dropped into run/resourcepacks.
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
            onlyIf { sharedSmokePacksDir.asFile.isDirectory }
        }
        val syncBook = nodeProject.tasks.register("syncSmokeBookId", Copy::class.java) {
            group = "verification"
            description = "Copy shared dev/smoke/book.txt into :$nodeName's run/book.txt"
            from(sharedSmokeBookFile)
            into(nodeProject.layout.projectDirectory.dir("run"))
            rename { "book.txt" }
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
            onlyIf { sharedSmokeBookFile.asFile.isFile }
        }
        nodeProject.tasks.matching {
            it.name == "runClient" || it.name == "runClientAutoSmoke"
        }.configureEach {
            dependsOn(syncPacks, syncBook)
        }
    }
}

if (smokeSyncNodes.isNotEmpty()) {
    tasks.register("syncSmokeResourcePacks") {
        group = "verification"
        description = "Copy shared dev/smoke/resourcepacks into every node's run directory"
        dependsOn(smokeSyncNodes.map { ":$it:syncSmokeResourcePacks" })
    }
    tasks.register("syncSmokeBookId") {
        group = "verification"
        description = "Copy shared dev/smoke/book.txt into every node's run directory"
        dependsOn(smokeSyncNodes.map { ":$it:syncSmokeBookId" })
    }
}
