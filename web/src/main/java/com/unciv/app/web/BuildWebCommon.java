package com.unciv.app.web;

import com.github.xpenatan.gdx.teavm.backends.shared.config.AssetFileHandle;
import com.github.xpenatan.gdx.teavm.backends.web.config.TeaBuildConfiguration;
import com.github.xpenatan.gdx.teavm.backends.web.config.TeaBuilder;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.teavm.tooling.TeaVMTargetType;
import org.teavm.tooling.TeaVMTool;
import org.teavm.vm.TeaVMOptimizationLevel;

final class BuildWebCommon {
    private static final String OUTPUT_NAME = "unciv";
    private static final List<String> REFLECTION_PREFIXES = List.of("com.badlogic.gdx.scenes.scene2d");

    private BuildWebCommon() {
    }

    static void build(boolean wasm) {
        Path repoRoot = Paths.get("").toAbsolutePath().normalize();
        Path assetsPath = repoRoot.resolve("android/assets");
        Path outputPath = repoRoot.resolve("web/build/dist");
        Path webappPath = outputPath.resolve("webapp");
        Path resourcesPath = repoRoot.resolve("web/build/resources/main");

        cleanupOutput(outputPath);
        ensureDirectory(outputPath);

        ensureDirectory(resourcesPath);

        TeaBuildConfiguration configuration = new TeaBuildConfiguration();
        configuration.webappPath = outputPath.toString();
        configuration.targetType = wasm ? TeaVMTargetType.WEBASSEMBLY_GC : TeaVMTargetType.JAVASCRIPT;
        configuration.targetFileName = OUTPUT_NAME;
        configuration.htmlTitle = "Unciv";
        configuration.htmlWidth = 0;
        configuration.htmlHeight = 0;
        configuration.assetsPath.add(new AssetFileHandle(assetsPath.toString()));

        TeaBuilder.config(configuration);

        TeaVMTool tool = new TeaVMTool();
        tool.setMainClass(WebLauncher.class.getName());
        tool.setOptimizationLevel(TeaVMOptimizationLevel.SIMPLE);
        tool.setObfuscated(false);
        if (!wasm) {
            tool.setDebugInformationGenerated(true);
            tool.setSourceMapsFileGenerated(true);
        }

        TeaBuilder.build(tool);
        flattenWebapp(webappPath, outputPath);
        sanitizeAtlasFiltersForWeb(outputPath.resolve("assets"));
    }

    private static void ensureDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed creating directory " + path, e);
        }
    }

    private static void cleanupOutput(Path outputPath) {
        if (!Files.exists(outputPath)) return;
        deleteRecursively(outputPath);
    }

    private static void flattenWebapp(Path webappPath, Path outputPath) {
        if (!Files.isDirectory(webappPath)) {
            throw new IllegalStateException("TeaVM output directory not found: " + webappPath);
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(webappPath)) {
            for (Path child : stream) {
                Path target = outputPath.resolve(child.getFileName());
                if (Files.exists(target)) {
                    deleteRecursively(target);
                }
                Files.move(child, target);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to flatten TeaVM webapp directory", e);
        }
        deleteRecursively(webappPath);
    }

    private static void deleteRecursively(Path path) {
        try {
            if (!Files.exists(path)) return;
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach(current -> {
                        try {
                            Files.deleteIfExists(current);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed deleting " + current, e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException("Failed deleting path " + path, e);
        }
    }

    /**
     * TeaVM/WebGL currently logs GL_INVALID_ENUM for mipmap filter tokens from atlas headers.
     * Rewrite copied atlas files for web output only to avoid noisy warnings.
     */
    private static void sanitizeAtlasFiltersForWeb(Path assetsPath) {
        if (!Files.isDirectory(assetsPath)) return;
        try {
            Files.walk(assetsPath)
                    .filter(path -> Files.isRegularFile(path) && path.toString().toLowerCase(Locale.ROOT).endsWith(".atlas"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            String sanitized = content
                                    .replace("MipMapLinearLinear", "Linear")
                                    .replace("MipMapLinearNearest", "Linear")
                                    .replace("MipMapNearestLinear", "Nearest")
                                    .replace("MipMapNearestNearest", "Nearest");
                            if (!sanitized.equals(content)) {
                                Files.writeString(path, sanitized);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException("Failed sanitizing atlas filters for " + path, e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException("Failed scanning atlas files in " + assetsPath, e);
        }
    }
}
