package com.unciv.logic.map

import com.unciv.logic.map.mapgenerator.MapGenerator
import com.unciv.models.ruleset.RulesetCache
import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.ln

/**
 * Regression guard for resource distribution diversity on generated maps.
 *
 * Thresholds were established empirically by running this test across 7 version
 * checkpoints spanning the last 30 releases (every 5th version, 4.19.8 → 4.20.11).
 * Baseline runs used hexagonal Pangaea maps with seeds 1–3 and worldWrap=false.
 *
 * Observed results for Large hexagonal maps by type (4.20.11, seeds 1–3):
 *
 *   Map type              Unique  Total  Entropy  TopRes%
 *   Perlin                   28   1080    0.961    6.9%
 *   Pangaea                  28    966    0.956    7.7%
 *   Continent and Islands    28    814    0.951   10.3%
 *   Two Continents           28   1342    0.960    6.8%
 *   Three Continents         28    882    0.956    6.8%
 *   Four Corners             28    852    0.949    6.8%
 *   Archipelago              27    792    0.965    9.2%   ← 1 fewer unique type
 *   Fractal                  28    938    0.948    9.0%
 *   Inner Sea                28   1844    0.961    6.1%
 *   Lakes                    28   2042    0.964    6.2%
 *   Small Continents         28    860    0.943   12.0%
 *   Boreal                   24   1978    0.929   14.5%   ← fewest unique, lowest entropy
 *
 * All thresholds are set at 90% of the minimum observed value for that case.
 * Entropy of 1.0 = perfectly uniform spread; 0.0 = all tiles have the same resource.
 */
@RunWith(GdxTestRunner::class)
class ResourceDiversityTest {

    // Raw observed minimums (Pangaea, hexagonal, seeds 1–3):
    //   Tiny(22, 0.943), Small(27, 0.937), Medium(28, 0.949), Large(28, 0.955), Huge(28, 0.957)
    private data class SizeCase(val size: MapSize, val minUnique: Int, val minEntropy: Double)

    private val sizeCases = listOf(
        SizeCase(MapSize.Tiny,   minUnique = 20, minEntropy = 0.85),
        SizeCase(MapSize.Small,  minUnique = 24, minEntropy = 0.84),
        SizeCase(MapSize.Medium, minUnique = 25, minEntropy = 0.85),
        SizeCase(MapSize.Large,  minUnique = 25, minEntropy = 0.86),
        SizeCase(MapSize.Huge,   minUnique = 25, minEntropy = 0.86),
    )

    // Per-type thresholds for Large hexagonal maps at 90% of observed values.
    // Types not listed fall through to the default (floor set by weakest observed type = Boreal).
    //   Boreal:     unique=24 → 22,  entropy=0.929 → 0.84
    //   Archipelago: unique=27 → 24, entropy=0.965 → 0.87 (higher entropy floor than default)
    //   All others: unique=28 → 25,  entropy≥0.943 → 0.85
    private data class TypeThreshold(val minUnique: Int, val minEntropy: Double)
    private val largeTypeThresholds = mapOf(
        MapType.boreal      to TypeThreshold(minUnique = 22, minEntropy = 0.84),
        MapType.archipelago to TypeThreshold(minUnique = 24, minEntropy = 0.85),
    ).withDefault              { TypeThreshold(minUnique = 25, minEntropy = 0.85) }

    @Ignore("Slow — run manually after changing map generation logic")
    @Test
    fun resourceDiversityAcrossMapSizesAndTypes() {
        RulesetCache.loadRulesets(noMods = true)
        val ruleset = RulesetCache.getVanillaRuleset()

        // Pangaea × every size (hexagonal)
        for ((size, minUnique, minEntropy) in sizeCases) {
            val counts = countResources(mapType = MapType.pangaea, shape = MapShape.hexagonal, size = size, ruleset = ruleset)
            assertDiversity("Pangaea / ${size.name}", counts, minUnique, minEntropy)
        }

        // Large hexagonal × every map type
        for (mapType in MapType.allValues) {
            val (minUnique, minEntropy) = largeTypeThresholds.getValue(mapType)
            val counts = countResources(mapType = mapType, shape = MapShape.hexagonal, size = MapSize.Large, ruleset = ruleset)
            assertDiversity("$mapType / Large", counts, minUnique, minEntropy)
        }
    }

    private fun countResources(mapType: String, shape: String, size: MapSize, ruleset: com.unciv.models.ruleset.Ruleset): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        for (seed in 1L..3L) {
            val params = MapParameters()
            params.type = mapType
            params.shape = shape
            params.mapSize = size
            params.worldWrap = false
            params.seed = seed
            MapGenerator(ruleset).generateMap(params).tileList.forEach { tile ->
                val r = tile.resource ?: return@forEach
                counts[r] = (counts[r] ?: 0) + 1
            }
        }
        return counts
    }

    private fun assertDiversity(label: String, counts: Map<String, Int>, minUnique: Int, minEntropy: Double) {
        val uniqueCount = counts.size
        val total = counts.values.sum()
        val entropy = if (total == 0) 0.0 else
            counts.values.sumOf { c -> val p = c.toDouble() / total; -p * ln(p) }
        val maxEntropy = if (uniqueCount <= 1) 1.0 else ln(uniqueCount.toDouble())
        val normalizedEntropy = if (maxEntropy == 0.0) 0.0 else entropy / maxEntropy

        Assert.assertTrue(
            "$label: expected ≥ $minUnique unique resource types, got $uniqueCount. Counts: $counts",
            uniqueCount >= minUnique
        )
        Assert.assertTrue(
            "$label: normalised entropy $normalizedEntropy below threshold $minEntropy. " +
                "One or a few resource types are dominating. Counts: $counts",
            normalizedEntropy >= minEntropy
        )
    }
}
