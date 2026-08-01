plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "java21-1.21.1"

// Chiseled multi-version build (SC 0.6 API)
stonecutter.registerChiseled(tasks.register("chiseledBuild", stonecutter.chiseled) {
    group = "project"
    ofTask("build")
})
