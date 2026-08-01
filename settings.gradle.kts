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
        id("fabric-loom") version "1.13.6"
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
        // java21 toolchain branch — nodes under versions/
        vers("1.21.1", "1.21.1")
        vers("1.21.1-fabric", "1.21.1")
        vers("1.21.4", "1.21.4")
        vers("1.21.4-fabric", "1.21.4")
        vers("1.21.11-fabric", "1.21.11")
    }
    create(rootProject)
}

rootProject.name = "flexibook"
