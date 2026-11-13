pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.minecraftforge.net")
        maven("https://maven.parchmentmc.org")
        maven("https://maven.fabricmc.net/")
    }
    plugins {
        id("fabric-loom") version "1.13.6"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
    id("dev.kikugie.stonecutter") version "0.6.2"
}

// Legacy build definition: 1.20.1 Forge/Fabric only.
// Invoke it through the Gradle 8.14.5 wrapper with this settings file:
//   legacy-gradle8/gradlew -c settings.legacy.gradle.kts :1.20.1:build
// It shares the same root src/ tree; Stonecutter nodes stay in versions/.
stonecutter {
    kotlinController = true
    centralScript = "build.legacy.gradle"
    shared {
        // Java 17 toolchain nodes under versions/
        vers("1.20.1", "1.20.1")
        vers("1.20.1-fabric", "1.20.1")
    }
    create(rootProject)
}

rootProject.name = "flexibook-legacy-1.20.1"
