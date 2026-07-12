package top.maplex.slimeEasy;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * 在插件主类加载前提供 Kotlin 运行库，避免把 Kotlin 打入插件 Jar。
 *
 * <p>该类必须保持为纯 Java：Paper 执行 Loader 时 Kotlin 运行库尚未加入插件类路径。</p>
 */
public final class SlimeEasyLoader implements PluginLoader {

    /**
     * 必须与 {@code gradle/libs.versions.toml} 的 {@code versions.kotlin} 保持一致，确保编译期与运行期 ABI 相同。
     */
    private static final String KOTLIN_VERSION = "2.4.20-Beta1";

    @Override
    public void classloader(final PluginClasspathBuilder builder) {
        final MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addDependency(new Dependency(
            new DefaultArtifact("org.jetbrains.kotlin:kotlin-stdlib-jdk8:" + KOTLIN_VERSION),
            null
        ));

        // 使用 Paper 提供的 Maven Central 镜像地址，以遵循服务端统一的仓库重定向策略。
        resolver.addRepository(new RemoteRepository.Builder(
            "central",
            "default",
            MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR
        ).build());
        builder.addLibrary(resolver);
    }
}
