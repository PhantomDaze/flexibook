plugins {
    id("dev.kikugie.stonecutter")
}

// Default IDE/configuration node only; build tasks preprocess each node independently.
stonecutter active "26.1.2"

// Chiseled multi-version build (SC 0.6 API)
stonecutter.registerChiseled(tasks.register("chiseledBuild", stonecutter.chiseled) {
    group = "project"
    ofTask("build")
})

/**
 * Sequential client smoke: NeoForge/Fabric × 26.1.2/26.2.
 * Each client is started with flexibook.autoSmoke (create world → demo book → read 10s → quit).
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
