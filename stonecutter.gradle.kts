plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.20.1"

// Chiseled multi-version build (SC 0.6 API)
stonecutter.registerChiseled(tasks.register("chiseledBuild", stonecutter.chiseled) {
    group = "project"
    ofTask("build")
})
