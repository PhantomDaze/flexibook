plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.1"

// Chiseled multi-version build (SC 0.6 API)
stonecutter.registerChiseled(tasks.register("chiseledBuild", stonecutter.chiseled) {
    group = "project"
    ofTask("build")
})

/**
 * Sequential client smoke: NeoForge/Fabric x 1.21.1/1.21.4/1.21.11.
 * Each client creates a fresh world, opens the demo book, reads for 10s, and exits.
 */
tasks.register("autoSmokeAllClients") {
    group = "verification"
    description = "Run clientAutoSmoke on every Java 21 version node, one after another"
    doLast {
        val nodes = listOf("1.21.1", "1.21.1-fabric", "1.21.4", "1.21.4-fabric", "1.21.11-fabric")
        val isWindows = org.gradle.internal.os.OperatingSystem.current().isWindows
        val gradlewName = if (isWindows) "gradlew.bat" else "gradlew"
        val gradlewPath = rootDir.resolve(gradlewName).absolutePath
        nodes.forEach { node ->
            logger.lifecycle("")
            logger.lifecycle("========== autoSmoke :$node:runClientAutoSmoke ==========")
            val command = if (isWindows) {
                listOf("cmd", "/c", gradlewPath, ":$node:runClientAutoSmoke", "--console=plain")
            } else {
                listOf(gradlewPath, ":$node:runClientAutoSmoke", "--console=plain")
            }
            val process = ProcessBuilder(command)
                    .directory(rootDir)
                    .inheritIO()
                    .start()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw GradleException(":$node:runClientAutoSmoke failed with exit code $exitCode")
            }
            logger.lifecycle("========== finished :$node:runClientAutoSmoke ==========")
        }
        logger.lifecycle("")
        logger.lifecycle("autoSmokeAllClients: all nodes completed")
    }
}
