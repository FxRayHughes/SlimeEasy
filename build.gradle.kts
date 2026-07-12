plugins {
    kotlin("jvm") version libs.versions.kotlin.get()
    alias(libs.plugins.run.paper)
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    // DecentHolograms 通过 JitPack 发布；这里只解析编译期 API，不把插件打入产物。
    maven("https://jitpack.io/")
}

dependencies {
    // dev bundle 同时提供 Paper API、CraftBukkit 与 Mojang 映射 NMS，不能再重复声明 paper-api。
    paperweight.paperDevBundle(libs.versions.paper.get())
    // Slimefun 由服务器提供，仅编译期引入；限定名称避免把服务端 bundler 误放进编译类路径。
    compileOnly(fileTree("libs") { include("Slimefun-*.jar") })
    // 运行时由服务器可选提供；关闭传递依赖，避免 NBT-API 等实现细节进入本插件类路径。
    compileOnly("com.github.decentsoftware-eu:decentholograms:2.10.1")
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
