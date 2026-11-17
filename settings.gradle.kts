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
        // 13 nodes in the main build. The two Java 17 nodes (1.20.1, 1.20.1-fabric)
        // live in the nested legacy-gradle8 build: ForgeGradle 6 cannot run on
        // Gradle 9.6.1 (MCPRuntime uses project services after they are closed).
        // Pass A — Java 21 toolchain (JDK21 daemon):
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
        vers("26.3", "26.3")
        vers("26.3-fabric", "26.3")
    }
    create(rootProject)
}

rootProject.name = "flexibook"
