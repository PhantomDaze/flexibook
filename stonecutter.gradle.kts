plugins {
    id("dev.kikugie.stonecutter")
}

// Default IDE/configuration node only; build tasks preprocess each node independently.
// The legacy 1.20.1 build (-c settings.legacy.gradle.kts) has a different node set,
// so its driver passes -Pstonecutter.active=1.20.1-fabric.
val activeNode = providers.gradleProperty("stonecutter.active").orNull ?: "26.1.2"
stonecutter active activeNode

// Chiseled multi-version build (SC 0.6 API).
// NOTE: chiseledBuild visits every node in ONE Gradle daemon. That is fine for the
// configuration/compile graph, but the 26.x nodes need a JDK25 daemon while
// 1.20.1/1.21.x need JDK21 — use scripts/build-all.sh for real two-pass builds.
stonecutter.registerChiseled(tasks.register("chiseledBuild", stonecutter.chiseled) {
    group = "project"
    ofTask("build")
})

// Two-pass build meta tasks (JVM selection still comes from JAVA_HOME):
//   JAVA_HOME=<jdk21> ./gradlew buildPassA
//   JAVA_HOME=<jdk25> ./gradlew buildPassB
val passANodes = listOf(
    "1.21.1", "1.21.1-fabric", "1.21.4", "1.21.4-fabric", "1.21.11-fabric"
)
val passBNodes = listOf("26.1.2", "26.1.2-fabric", "26.2", "26.2-fabric")

tasks.register("buildPassA") {
    group = "project"
    description = "Build the 5 Java 21 nodes (run with JAVA_HOME=<jdk21>; 1.20.1 lives in legacy-gradle8)"
    dependsOn(passANodes.map { ":$it:build" })
}
tasks.register("buildPassB") {
    group = "project"
    description = "Build the 4 Java 25 nodes (run with JAVA_HOME=<jdk25>)"
    dependsOn(passBNodes.map { ":$it:build" })
}

/**
 * Sequential client smoke: NeoForge/Fabric × 26.1.2/26.2.
 * Each client is started with flexibook.autoSmoke (create world → demo book → read 10s → quit).
 *
 * TODO(unified-smoke): this is the pre-merge main-branch task (26.x only).
 * The next task must merge the java21 AutoSmokeClient ≥1.21.4 conditions (16b990e)
 * and the 1.20.1 book.txt/resourcepack sync (da16b22) into one pass-grouped task set.
 */
tasks.register("autoSmokeClients") {
    group = "verification"
    description = "Run clientAutoSmoke on every version node, one after another"
    doLast {
        val nodes = listOf("26.1.2", "26.2", "26.1.2-fabric", "26.2-fabric")
        val isWindows = org.gradle.internal.os.OperatingSystem.current().isWindows
        val gradlewName = if (isWindows) "gradlew.bat" else "gradlew"
        val gradlewPath = rootDir.resolve(gradlewName).absolutePath
        nodes.forEach { node ->
            logger.lifecycle("")
            logger.lifecycle("========== autoSmoke :$node:runClientAutoSmoke ==========")
            val pb = ProcessBuilder(
                if (isWindows) listOf("cmd", "/c", gradlewPath, ":$node:runClientAutoSmoke", "--console=plain")
                else listOf(gradlewPath, ":$node:runClientAutoSmoke", "--console=plain")
            )
            pb.directory(rootDir)
            pb.inheritIO()
            val proc = pb.start()
            val code = proc.waitFor()
            if (code != 0) {
                throw GradleException(":$node:runClientAutoSmoke failed with exit code $code")
            }
            logger.lifecycle("========== finished :$node:runClientAutoSmoke ==========")
        }
        logger.lifecycle("")
        logger.lifecycle("autoSmokeClients: all nodes completed")
    }
}
