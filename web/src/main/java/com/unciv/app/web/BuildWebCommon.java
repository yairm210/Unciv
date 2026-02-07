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
    private static final List<String> PRESERVED_CLASSES = List.of(
            "com.badlogic.gdx.scenes.scene2d.ui.Skin",
            "com.unciv.models.stats.NamedStats",
            "com.unciv.models.ruleset.ModOptions",
            "com.unciv.models.ruleset.TechColumn",
            "com.unciv.models.ruleset.tech.Technology",
            "com.unciv.models.ruleset.Building",
            "com.unciv.models.ruleset.tile.Terrain",
            "com.unciv.models.ruleset.tile.TileResource",
            "com.unciv.models.ruleset.tile.TileImprovement",
            "com.unciv.models.ruleset.tech.Era",
            "com.unciv.models.ruleset.Speed",
            "com.unciv.models.ruleset.unit.UnitType",
            "com.unciv.models.ruleset.unit.BaseUnit",
            "com.unciv.models.ruleset.unit.Promotion",
            "com.unciv.models.ruleset.unit.UnitNameGroup",
            "com.unciv.models.ruleset.Quest",
            "com.unciv.models.ruleset.Specialist",
            "com.unciv.models.ruleset.PolicyBranch",
            "com.unciv.models.ruleset.Policy",
            "com.unciv.models.ruleset.Belief",
            "com.unciv.models.ruleset.RuinReward",
            "com.unciv.models.ruleset.nation.Nation",
            "com.unciv.models.ruleset.nation.Difficulty",
            "com.unciv.models.ruleset.GlobalUniques",
            "com.unciv.models.ruleset.Victory",
            "com.unciv.models.ruleset.nation.CityStateType",
            "com.unciv.models.ruleset.nation.Personality",
            "com.unciv.models.ruleset.Event",
            "com.unciv.models.ruleset.EventChoice",
            "com.unciv.models.ruleset.Tutorial",
            "com.unciv.models.metadata.GameParameters",
            "com.unciv.logic.map.MapParameters");

    private BuildWebCommon() {
    }

    static void build(boolean wasm) {
        Path repoRoot = Paths.get("").toAbsolutePath().normalize();
        Path assetsPath = repoRoot.resolve("android/assets");
        Path outputPath = repoRoot.resolve("web/build/dist");
        Path webappPath = outputPath.resolve("webapp");

        cleanupOutput(outputPath);
        ensureDirectory(outputPath);

        TeaBuildConfiguration configuration = new TeaBuildConfiguration();
        configuration.webappPath = outputPath.toString();
        configuration.targetType = wasm ? TeaVMTargetType.WEBASSEMBLY_GC : TeaVMTargetType.JAVASCRIPT;
        configuration.targetFileName = OUTPUT_NAME;
        configuration.htmlTitle = "Unciv";
        configuration.htmlWidth = 0;
        configuration.htmlHeight = 0;
        configuration.assetsPath.add(new AssetFileHandle(assetsPath.toString()));
        configuration.classesToPreserve.addAll(PRESERVED_CLASSES);

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
        if (!wasm) {
            hardenIndexBootstrap(outputPath.resolve("index.html"));
        }
        sanitizeAtlasFiltersForWeb(outputPath.resolve("assets"));
        ensureFavicon(outputPath);
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

    private static void ensureFavicon(Path outputPath) {
        Path faviconPath = outputPath.resolve("favicon.ico");
        if (Files.exists(faviconPath)) return;
        try {
            Files.write(faviconPath, new byte[] {0});
        } catch (IOException e) {
            throw new RuntimeException("Failed creating favicon at " + faviconPath, e);
        }
    }

    /**
     * Some browsers/runtimes can miss the plain load-listener bootstrap generated by TeaVM.
     * Replace with a guarded bootstrap that starts once and retries until `main` is available.
     */
    private static void hardenIndexBootstrap(Path indexPath) {
        if (!Files.isRegularFile(indexPath)) return;
        try {
            String content = Files.readString(indexPath);
            String legacy =
                    "<script>\n"
                            + "            async function start() {\n"
                            + "                main()\n"
                            + "            }\n"
                            + "            window.addEventListener(\"load\", start);\n"
                            + "        </script>";
            String hardened =
                    "<script>\n"
                            + "            (function () {\n"
                            + "                function boot() {\n"
                            + "                    if (window.__uncivBootStarted) return;\n"
                            + "                    if (typeof window.main !== 'function') {\n"
                            + "                        setTimeout(boot, 25);\n"
                            + "                        return;\n"
                            + "                    }\n"
                            + "                    window.__uncivBootStarted = true;\n"
                            + "                    window.main();\n"
                            + "                }\n"
                            + "                if (document.readyState === 'complete') {\n"
                            + "                    setTimeout(boot, 0);\n"
                            + "                } else {\n"
                            + "                    window.addEventListener('load', boot, { once: true });\n"
                            + "                }\n"
                            + "            })();\n"
                            + "        </script>";
            if (content.contains(legacy)) {
                content = content.replace(legacy, hardened);
            } else if (!content.contains("__uncivBootStarted")) {
                content = content.replace("</body>", hardened + "\n    </body>");
            }
            Files.writeString(indexPath, content);
        } catch (IOException e) {
            throw new RuntimeException("Failed hardening index bootstrap at " + indexPath, e);
        }
    }
}
