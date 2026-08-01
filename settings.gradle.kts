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
        // java25 toolchain branch — nodes under versions/
        vers("26.1.2", "26.1.2")
        vers("26.2", "26.2")
    }
    create(rootProject)
}

rootProject.name = "flexibook"
