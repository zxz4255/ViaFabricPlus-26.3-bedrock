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
        // CubeConverter（ViaBedrock 运行期需要的模型互转库）：仓库内 vendored 本地 maven 仓库优先。
        // 必须以 module component 形式解析，否则 Fabric Loom 的 processIncludeJars 会报
        // "which is not a module component and has no capabilities"（file 依赖没有 capability）。
        maven(url = file("libs/maven")) {
            content {
                includeGroup("com.github.oryxel1")
            }
        }
        // 备用：万一本地仓库那份没同步上，从 JitPack 取同一个坐标
        maven("https://jitpack.io") {
            content {
                includeGroupByRegex("com\\.github\\..+")
            }
        }
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
