package com.unciv.logic.map.mapgenerator.resourceplacement

import com.unciv.logic.map.MapSize
import com.unciv.logic.map.mapgenerator.MapResourceSetting
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.UniqueType
import kotlin.math.max
import kotlin.math.min

/**
 * Civ5 AssignStartingPlots resource-density tables (BNW / Lekmap values).
 * Used when ModOptions has [UniqueType.Civ5StyleMapResourceGeneration].
 *
 * Regional luxury tables are indexed by major-civ count 1..22 (index 0 unused),
 * matching Lua `target_list[iNumCivs]`.
 */
object Civ5LuxuryTargetNumbers {

    /** Resource-setting delta applied to regional luxury targets in Civ5 PlaceLuxuries. */
    fun regionalResourceSettingDelta(setting: MapResourceSetting): Int = when (setting) {
        MapResourceSetting.sparse -> -2
        MapResourceSetting.abundant -> 2
        else -> 0
    }

    /**
     * Civ5 `bonus_multiplier` for PlaceStrategicAndBonusResources.
     * Lower → denser placement (multiplies frequency / "tiles per resource").
     */
    fun civ5BonusFrequencyMultiplier(setting: MapResourceSetting): Float = when (setting) {
        MapResourceSetting.sparse -> 1f // Civ5 setting 1
        MapResourceSetting.abundant -> 0.35f // Civ5 setting 8
        else -> 0.65f // Civ5 default (~setting 5)
    }

    /**
     * Effective tiles-per-resource multiplier for bonus and minor strategic placement.
     * [percentModifier] > 1 means denser (more resources), matching ModOptions `[relativeAmount]% …`.
     */
    fun effectiveBonusFrequencyMultiplier(
        setting: MapResourceSetting,
        useCiv5: Boolean,
        percentModifier: Float = 1f,
    ): Float {
        val base = if (useCiv5) civ5BonusFrequencyMultiplier(setting) else setting.bonusFrequencyMultiplier
        val scaled = if (percentModifier <= 0f) base else base / percentModifier
        return scaled.coerceAtLeast(0.05f)
    }

    fun mapGenPercentModifier(ruleset: Ruleset, type: UniqueType): Float {
        val unique = ruleset.modOptions.getMatchingUniques(type).firstOrNull() ?: return 1f
        return unique.params[0].toFloat() / 100f
    }

    fun resolveMapSize(mapSize: MapSize): MapSize.Predefined = mapSize.getPredefinedOrNextSmaller()

    fun regionalTarget(
        mapSize: MapSize,
        civCount: Int,
        resourceSetting: MapResourceSetting,
        percentModifier: Float = 1f,
    ): Int {
        val table = regionalTable(resolveMapSize(mapSize))
        val clampedCivs = civCount.coerceIn(1, table.lastIndex)
        var target = table[clampedCivs] + regionalResourceSettingDelta(resourceSetting)
        target = (target * percentModifier + 0.5f).toInt()
        return max(1, target)
    }

    /**
     * @return Pair(worldTotalTarget, minimumRandomLoopHelper) matching Civ5 GetWorldLuxuryTargetNumbers.
     */
    fun worldTarget(
        mapSize: MapSize,
        resourceSetting: MapResourceSetting,
        percentModifier: Float = 1f,
    ): Pair<Int, Int> {
        val (sparse, standard, abundant) = worldTable(resolveMapSize(mapSize))
        val (total, loopMin) = when (resourceSetting) {
            MapResourceSetting.sparse -> sparse
            MapResourceSetting.abundant -> abundant
            else -> standard
        }
        return max(0, (total * percentModifier + 0.5f).toInt()) to loopMin
    }

    /** Random luxuries still needed to approach the world target after starts/CS/regional. */
    fun randomTopUpTarget(worldTotalTarget: Int, alreadyPlaced: Int, extraVariance: Int): Int =
        max(0, worldTotalTarget + extraVariance - alreadyPlaced)

    fun targetForRandomLuxuryType(
        index: Int,
        typeCount: Int,
        totalRandomTarget: Int,
        loopMin: Int,
        ratios: Map<Int, List<Float>>,
    ): Int {
        if (typeCount <= 0) return 0
        val ratioList = ratios[typeCount]
        if (ratioList == null || typeCount > 8) return max(loopMin, (totalRandomTarget + 9) / 10)
        val minimum = max(3, loopMin - index)
        val share = (totalRandomTarget * ratioList[min(index, ratioList.lastIndex)] + 0.5f).toInt()
        return max(minimum, share)
    }

    private fun regionalTable(size: MapSize.Predefined): IntArray = when (size) {
        // Civ5 Tiny
        MapSize.Predefined.Tiny -> intArrayOf(0, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)
        // Civ5 Small
        MapSize.Predefined.Small -> intArrayOf(0, 3, 3, 3, 4, 4, 4, 3, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)
        // Civ5 Standard (Unciv Medium)
        MapSize.Predefined.Medium -> intArrayOf(0, 3, 3, 4, 4, 5, 5, 6, 5, 5, 4, 4, 3, 3, 2, 2, 1, 1, 1, 1, 1, 1)
        // Civ5 Large
        MapSize.Predefined.Large -> intArrayOf(0, 3, 4, 4, 5, 5, 5, 6, 6, 7, 6, 6, 5, 5, 4, 4, 3, 3, 2, 2, 2, 2)
        // Civ5 Huge
        MapSize.Predefined.Huge -> intArrayOf(0, 4, 5, 5, 6, 6, 6, 6, 7, 7, 7, 8, 7, 7, 6, 6, 5, 5, 4, 4, 3, 3)
    }

    private fun worldTable(size: MapSize.Predefined): Triple<Pair<Int, Int>, Pair<Int, Int>, Pair<Int, Int>> =
        when (size) {
            MapSize.Predefined.Tiny -> Triple(24 to 4, 35 to 4, 40 to 4)
            MapSize.Predefined.Small -> Triple(36 to 4, 60 to 5, 80 to 5)
            MapSize.Predefined.Medium -> Triple(48 to 5, 60 to 5, 80 to 5)
            MapSize.Predefined.Large -> Triple(60 to 5, 88 to 5, 100 to 5)
            MapSize.Predefined.Huge -> Triple(76 to 6, 112 to 6, 128 to 6)
        }
}
