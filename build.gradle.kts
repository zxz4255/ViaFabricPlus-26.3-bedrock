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
    }
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
