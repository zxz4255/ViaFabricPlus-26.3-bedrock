pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
    }
}

plugins {
    id("base.fabric_settings")
}

dependencyResolutionManagement {
    repositories {
        maven("https://repo.viaversion.com")
        maven("https://maven.lenni0451.net/everything")
        maven("https://maven.terraformersmc.com/releases")
        maven("https://repo.opencollab.dev/maven-snapshots") {
            content {
                includeGroupByRegex("org\\.cloudburstmc\\..+")
                includeGroup("dev.opencollab")
            }
        }
        //mavenLocal() // Uncomment during Minecraft updates for preview VV/VB builds
    }
}

rootProject.name = "viafabricplus"

include("viafabricplus-api")
