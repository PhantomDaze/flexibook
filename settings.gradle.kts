pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.minecraftforge.net")
        maven("https://maven.parchmentmc.org")
        maven("https://maven.fabricmc.net/")
    }
    plugins {
        id("net.neoforged.moddev") version "2.0.143"
        // Loom 1.17+ splits plugins:
        // - net.fabricmc.fabric-loom       = NoRemap (MC 26.x unobfuscated)
        // - net.fabricmc.fabric-loom-remap = classic remap + mappings
        // - fabric-loom                   = legacy alias of the remap plugin
        id("net.fabricmc.fabric-loom") version "1.17.17"
        id("net.fabricmc.fabric-loom-remap") version "1.17.17"
        id("fabric-loom") version "1.17.17"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
    id("dev.kikugie.stonecutter") version "0.6.2"
}

stonecutter {
    kotlinController = true
    centralScript = "build.gradle"
    shared {
        // 11 version nodes (union of main / 1.20.1 / java21).
        // Pass A — Java 17/21 toolchains (JDK21 daemon):
        vers("1.20.1", "1.20.1")
        vers("1.20.1-fabric", "1.20.1")
        vers("1.21.1", "1.21.1")
        vers("1.21.1-fabric", "1.21.1")
        vers("1.21.4", "1.21.4")
        vers("1.21.4-fabric", "1.21.4")
        vers("1.21.11-fabric", "1.21.11")
        // Pass B — Java 25 toolchain (JDK25 daemon):
        vers("26.1.2", "26.1.2")
        vers("26.1.2-fabric", "26.1.2")
        vers("26.2", "26.2")
        vers("26.2-fabric", "26.2")
    }
    create(rootProject)
}

rootProject.name = "flexibook"
