plugins {
    id("configuration.transitive_jar_in_jar")
    id("via.maven_publish")
    id("base.junit")
    id("extra.unlock_build_errors")
}

// Comment during Minecraft updates to update data diff files
tasks.test {
    enabled = false
}

dependencies {
    jarInJar(projects.viafabricplusApi) {
        exclude("net.fabricmc", "fabric-loader")
    }

    jarInJar(platform(libs.fabric.api.bom))
    jarInJar(libs.fabric.api.base)
    jarInJar(libs.fabric.resource.loader.v1)
    jarInJar(libs.fabric.resource.loader.v0)
    jarInJar(libs.fabric.networking.api.v1)
    jarInJar(libs.fabric.command.api.v2)
    jarInJar(libs.fabric.lifecycle.events.v1)
    jarInJar(libs.fabric.particles.v1)
    jarInJar(libs.fabric.registry.sync.v0)

    jarInJar(libs.reflect)
    jarInJar(libs.classic4j)

    // Bedrock Edition support
    jarInJar(libs.viabedrock) {
        exclude(group = "com.mojang", module = "brigadier")
        exclude(group = "at.yawk.lz4", module = "lz4-java")
        exclude(group = "io.netty")
        // CubeConverter (JitPack) 在 pom 里把这三个声明成 runtime 依赖，实际都由宿主提供：
        // viaversion-common 用的是本工程的 5.12.3-SNAPSHOT，joml 由 Minecraft 自带，
        // lombok 只用于编译。排除掉，否则会被当成嵌套 jar 打进 mod 里造成版本冲突。
        exclude(group = "com.viaversion", module = "viaversion-common")
        exclude(group = "org.projectlombok", module = "lombok")
        exclude(group = "org.joml", module = "joml")
        // CubeConverter 只发布在 JitPack（CI 上不一定可达/可能排长队），这里改用仓库内 vendored 副本
        // libs/CubeConverter-abecffaaea.jar（与 ViaBedrock 0.0.29 pom 声明的版本一致）
        exclude(group = "com.github.oryxel1", module = "CubeConverter")
    }
    // 模型互转库（Bedrock .geo / Java 模型），ViaBedrock 运行期需要。
    // 走 libs/maven 这个本地仓库解析成 module component（file 依赖 loom 无法嵌套）
    jarInJar(libs.cubeconverter)
    jarInJar(libs.minecraftauth) {
        exclude(group = "com.google.code.gson", module = "gson")
    }
    jarInJar(libs.netty.transport.raknet) {
        exclude(group = "io.netty")
    }
    jarInJar(libs.netty.transport.nethernet) {
        exclude(group = "io.netty")
        exclude(group = "org.bouncycastle")
        exclude(group = "dev.opencollab", module = "libdatachannel-java")
    }
    jarInJar(libs.libdatachannel.java.arch.detect)

    compileOnly(libs.modmenu)
}
