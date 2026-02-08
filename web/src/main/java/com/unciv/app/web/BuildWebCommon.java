package com.unciv.app.web;

import com.github.xpenatan.gdx.teavm.backends.shared.config.AssetFileHandle;
import com.github.xpenatan.gdx.teavm.backends.web.config.TeaBuildConfiguration;
import com.github.xpenatan.gdx.teavm.backends.web.config.TeaBuilder;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.FileVisitOption;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.teavm.tooling.TeaVMTargetType;
import org.teavm.tooling.TeaVMTool;
import org.teavm.vm.TeaVMOptimizationLevel;

final class BuildWebCommon {
    private static final String OUTPUT_NAME = "unciv";
    private static final List<String> SCANNED_PACKAGE_PREFIXES = List.of("com/unciv/");
    private static final List<String> EXCLUDED_PRESERVE_CLASS_PREFIXES = List.of(
            "com.unciv.logic.multiplayer.",
            "com.unciv.ui.screens.devconsole.");
    private static final List<String> SERIALIZATION_MARKER_CLASS_NAMES = List.of(
            "com.unciv.logic.IsPartOfGameInfoSerialization",
            "com.unciv.models.ruleset.IRulesetObject",
            "com.badlogic.gdx.utils.Json$Serializable");
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
            "com.unciv.logic.GameInfo",
            "com.unciv.logic.VictoryData",
            "com.unciv.logic.map.TileMap",
            "com.unciv.logic.map.tile.Tile",
            "com.unciv.logic.map.mapunit.MapUnit",
            "com.unciv.logic.map.mapunit.MapUnit$UnitMovementMemory",
            "com.unciv.logic.map.mapunit.UnitPromotions",
            "com.unciv.logic.city.City",
            "com.unciv.logic.city.CityConstructions",
            "com.unciv.logic.civilization.Civilization",
            "com.unciv.logic.civilization.Civilization$NotificationsLog",
            "com.unciv.logic.civilization.Civilization$HistoricalAttackMemory",
            "com.unciv.logic.civilization.Notification",
            "com.unciv.logic.civilization.PopupAlert",
            "com.unciv.logic.civilization.ExploredRegion",
            "com.unciv.logic.civilization.CivRankingHistory",
            "com.unciv.logic.civilization.managers.VictoryManager",
            "com.unciv.logic.civilization.managers.EspionageManager",
            "com.unciv.logic.civilization.managers.ReligionManager",
            "com.unciv.logic.civilization.managers.QuestManager",
            "com.unciv.logic.civilization.managers.AssignedQuest",
            "com.unciv.logic.civilization.managers.TechManager",
            "com.unciv.logic.civilization.managers.GoldenAgeManager",
            "com.unciv.logic.civilization.managers.PolicyManager",
            "com.unciv.logic.civilization.managers.GreatPersonManager",
            "com.unciv.logic.civilization.CivConstructions",
            "com.unciv.logic.civilization.diplomacy.DiplomacyManager",
            "com.unciv.logic.city.managers.CityPopulationManager",
            "com.unciv.logic.city.managers.CityReligionManager",
            "com.unciv.logic.city.managers.CityEspionageManager",
            "com.unciv.logic.city.managers.CityExpansionManager",
            "com.unciv.logic.trade.Trade",
            "com.unciv.logic.trade.TradeRequest",
            "com.unciv.logic.trade.TradeOffer",
            "com.unciv.logic.trade.TradeOffersList",
            "com.unciv.logic.automation.civilization.BarbarianManager",
            "com.unciv.logic.automation.civilization.Encampment",
            "com.unciv.models.Religion",
            "com.unciv.models.Spy",
            "com.unciv.models.ruleset.unique.TemporaryUnique",
            "com.unciv.models.metadata.GameParameters",
            "com.unciv.logic.map.MapParameters");

    private BuildWebCommon() {
    }

    static void build(boolean wasm) {
        Path repoRoot = Paths.get("").toAbsolutePath().normalize();
        Path assetsPath = repoRoot.resolve("android/assets");
        Path outputPath = repoRoot.resolve("web/build/dist");
        Path webappPath = outputPath.resolve("webapp");
        Path webResourcesPath = repoRoot.resolve("web/build/resources/main");

        cleanupOutput(outputPath);
        ensureDirectory(outputPath);
        ensureDirectory(webResourcesPath);

        TeaBuildConfiguration configuration = new TeaBuildConfiguration();
        configuration.webappPath = outputPath.toString();
        configuration.targetType = wasm ? TeaVMTargetType.WEBASSEMBLY_GC : TeaVMTargetType.JAVASCRIPT;
        configuration.targetFileName = OUTPUT_NAME;
        configuration.htmlTitle = "Unciv";
        configuration.htmlWidth = 0;
        configuration.htmlHeight = 0;
        configuration.assetsPath.add(new AssetFileHandle(assetsPath.toString()));
        configuration.classesToPreserve.addAll(PRESERVED_CLASSES);
        configuration.classesToPreserve.addAll(discoverSerializableClasses());

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
     * Preserve all application classes under the given package path (e.g. "com/unciv/")
     * from the current Java classpath so TeaVM reflection-backed JSON loading does not
     * silently drop fields when new model classes are introduced.
     */
    private static Set<String> discoverClasspathClasses(List<String> packagePathPrefixes) {
        Set<String> discovered = new LinkedHashSet<>();
        String classPath = System.getProperty("java.class.path", "");
        if (classPath.isBlank()) return discovered;

        String[] entries = classPath.split(java.io.File.pathSeparator);
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) continue;
            Path path = Paths.get(entry);
            if (Files.isDirectory(path)) {
                discoverFromDirectory(path, packagePathPrefixes, discovered);
            } else if (entry.endsWith(".jar")) {
                discoverFromJar(path, packagePathPrefixes, discovered);
            }
        }
        return discovered;
    }

    private static Set<String> discoverSerializableClasses() {
        Set<String> classNames = discoverClasspathClasses(SCANNED_PACKAGE_PREFIXES);
        if (classNames.isEmpty()) return classNames;

        ClassLoader classLoader = BuildWebCommon.class.getClassLoader();
        List<Class<?>> markers = new ArrayList<>();
        for (String markerClassName : SERIALIZATION_MARKER_CLASS_NAMES) {
            Class<?> marker = tryLoadClass(classLoader, markerClassName);
            if (marker != null) markers.add(marker);
        }
        if (markers.isEmpty()) return Set.of();

        Set<String> preserved = new LinkedHashSet<>();
        for (String className : classNames) {
            if (isExcludedPreserveClass(className)) continue;
            Class<?> candidate = tryLoadClass(classLoader, className);
            if (candidate == null) continue;
            for (Class<?> marker : markers) {
                if (marker.isAssignableFrom(candidate)) {
                    preserved.add(className);
                    break;
                }
            }
        }
        return preserved;
    }

    private static boolean isExcludedPreserveClass(String className) {
        for (String excludedPrefix : EXCLUDED_PRESERVE_CLASS_PREFIXES) {
            if (className.startsWith(excludedPrefix)) return true;
        }
        return false;
    }

    private static Class<?> tryLoadClass(ClassLoader classLoader, String className) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void discoverFromDirectory(Path root, List<String> packagePathPrefixes, Set<String> output) {
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, new FileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String relative = root.relativize(file).toString().replace('\\', '/');
                    maybeAddClassName(relative, packagePathPrefixes, output);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // Best-effort discovery; keep build resilient.
        }
    }

    private static void discoverFromJar(Path jarPath, List<String> packagePathPrefixes, Set<String> output) {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            for (JarEntry entry : java.util.Collections.list(jarFile.entries())) {
                maybeAddClassName(entry.getName(), packagePathPrefixes, output);
            }
        } catch (IOException ignored) {
            // Best-effort discovery; keep build resilient.
        }
    }

    private static void maybeAddClassName(String entryName, List<String> packagePathPrefixes, Set<String> output) {
        if (entryName == null) return;
        boolean prefixMatch = false;
        for (String prefix : packagePathPrefixes) {
            if (entryName.startsWith(prefix)) {
                prefixMatch = true;
                break;
            }
        }
        if (!prefixMatch) return;
        if (!entryName.endsWith(".class")) return;
        if (entryName.equals("module-info.class")) return;
        String className = entryName.substring(0, entryName.length() - ".class".length()).replace('/', '.');
        output.add(className);
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
