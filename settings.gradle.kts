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
        // Disk layout groups by JDK toolchain prefix under versions/:
        //   java17-*  (JDK 17) | java21-*  (JDK 21) | java25-*  (JDK 25)
        // Project name ≠ MC version (second arg drives //? if version checks).
        vers("java17-1.20.1", "1.20.1")
        vers("java17-1.20.1-fabric", "1.20.1")
        vers("java21-1.21.1", "1.21.1")
        vers("java21-1.21.1-fabric", "1.21.1")
        vers("java21-1.21.4", "1.21.4")
        vers("java21-1.21.4-fabric", "1.21.4")
        vers("java21-1.21.11-fabric", "1.21.11")
        vers("java25-26.1.2", "26.1.2")
        vers("java25-26.2", "26.2")
    }
    create(rootProject)
}

rootProject.name = "flexibook"
