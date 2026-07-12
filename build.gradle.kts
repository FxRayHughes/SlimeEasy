plugins {
    kotlin("jvm") version libs.versions.kotlin.get()
    alias(libs.plugins.run.paper)
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // dev bundle 同时提供 Paper API、CraftBukkit 与 Mojang 映射 NMS，不能再重复声明 paper-api。
    paperweight.paperDevBundle(libs.versions.paper.get())
    // Slimefun 由服务器提供，仅编译期引入；限定名称避免把服务端 bundler 误放进编译类路径。
    compileOnly(fileTree("libs") { include("Slimefun-*.jar") })
    // 普通 Jar 不会内嵌 implementation 依赖；服务器运行时由 SlimeEasyLoader 下载同版本 stdlib。
    implementation(libs.kotlin.stdlib)
}

// Paper 26.1+ 只运行 Mojang 映射插件，主产物不得再走已废弃的 Spigot reobf 流程。
paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

kotlin {
    jvmToolchain(25)
}

// run-paper 直接注入 build/libs 中的开发插件，旧的手工副本会造成同名双重类加载器。
val cleanRunPluginCopies = tasks.register<Delete>("cleanRunPluginCopies") {
    delete(fileTree(layout.projectDirectory.dir("run/plugins")) {
        include("SlimeEasy-*.jar")
    })
}

tasks {
    runServer {
        dependsOn(cleanRunPluginCopies)
        minecraftVersion(libs.versions.minecraft.get())
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }
}
