package com.unciv.logic.map.mapgenerator.resourceplacement

import com.unciv.logic.map.MapSize
import com.unciv.logic.map.mapgenerator.MapResourceSetting
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly
import kotlin.math.max
import kotlin.math.min

/**
 * Civ5 AssignStartingPlots luxury target tables (BNW / Lekmap values).
 * Used when ModOptions has [UniqueType.Civ5StyleWorldLuxuryTargets].
 *
 * Tables are indexed by major-civ count 1..22 (index 0 unused), matching Lua `target_list[iNumCivs]`.
 */
object Civ5LuxuryTargetNumbers {

    /** Regional luxury copies per region, by Unciv map size name → Civ5 world size. */
    private val regionalBySize: Map<MapSize.Predefined, IntArray> = mapOf(
        // Civ5 Tiny
        MapSize.Predefined.Tiny to intArrayOf(0, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
        // Civ5 Small
        MapSize.Predefined.Small to intArrayOf(0, 3, 3, 3, 4, 4, 4, 3, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
        // Civ5 Standard (Unciv Medium)
        MapSize.Predefined.Medium to intArrayOf(0, 3, 3, 4, 4, 5, 5, 6, 5, 5, 4, 4, 3, 3, 2, 2, 1, 1, 1, 1, 1, 1),
        // Civ5 Large
        MapSize.Predefined.Large to intArrayOf(0, 3, 4, 4, 5, 5, 5, 6, 6, 7, 6, 6, 5, 5, 4, 4, 3, 3, 2, 2, 2, 2),
        // Civ5 Huge
        MapSize.Predefined.Huge to intArrayOf(0, 4, 5, 5, 6, 6, 6, 6, 7, 7, 7, 8, 7, 7, 6, 6, 5, 5, 4, 4, 3, 3),
    )

    /** World luxury total target (first) and random min-loop helper (second), by resource setting. */
    private data class WorldTargets(val sparse: Pair<Int, Int>, val standard: Pair<Int, Int>, val abundant: Pair<Int, Int>)

    private val worldBySize: Map<MapSize.Predefined, WorldTargets> = mapOf(
        MapSize.Predefined.Tiny to WorldTargets(24 to 4, 35 to 4, 40 to 4),
        MapSize.Predefined.Small to WorldTargets(36 to 4, 60 to 5, 80 to 5),
        MapSize.Predefined.Medium to WorldTargets(48 to 5, 60 to 5, 80 to 5),
        MapSize.Predefined.Large to WorldTargets(60 to 5, 88 to 5, 100 to 5),
        MapSize.Predefined.Huge to WorldTargets(76 to 6, 112 to 6, 128 to 6),
    )

    @Readonly
    fun resolveMapSize(mapSize: MapSize): MapSize.Predefined = mapSize.getPredefinedOrNextSmaller()

    /** Resource-setting delta applied to regional targets in Civ5 PlaceLuxuries. */
    @Pure
    fun regionalResourceSettingDelta(setting: MapResourceSetting): Int = when (setting) {
        MapResourceSetting.sparse -> -2
        MapResourceSetting.abundant -> 2
        else -> 0
    }

    @Readonly
    fun regionalTarget(
        mapSize: MapSize,
        civCount: Int,
        resourceSetting: MapResourceSetting,
        percentModifier: Float = 1f,
    ): Int {
        val size = resolveMapSize(mapSize)
        val table = regionalBySize.getValue(size)
        val clampedCivs = civCount.coerceIn(1, table.lastIndex)
        var target = table[clampedCivs] + regionalResourceSettingDelta(resourceSetting)
        target = (target * percentModifier + 0.5f).toInt()
        return max(1, target)
    }

    /**
     * @return Pair(worldTotalTarget, minimumRandomLoopHelper) matching Civ5 GetWorldLuxuryTargetNumbers.
     */
    @Readonly
    fun worldTarget(
        mapSize: MapSize,
        resourceSetting: MapResourceSetting,
        percentModifier: Float = 1f,
    ): Pair<Int, Int> {
        val size = resolveMapSize(mapSize)
        val targets = worldBySize.getValue(size)
        val (total, loopMin) = when (resourceSetting) {
            MapResourceSetting.sparse -> targets.sparse
            MapResourceSetting.abundant -> targets.abundant
            else -> targets.standard
        }
        val scaledTotal = max(0, (total * percentModifier + 0.5f).toInt())
        return scaledTotal to loopMin
    }

    /** Random luxuries still needed to approach the world target after starts/CS/regional. */
    @Pure
    fun randomTopUpTarget(worldTotalTarget: Int, alreadyPlaced: Int, extraVariance: Int): Int =
        max(0, worldTotalTarget + extraVariance - alreadyPlaced)

    @Pure
    fun targetForRandomLuxuryType(
        index: Int,
        typeCount: Int,
        totalRandomTarget: Int,
        loopMin: Int,
        ratios: Map<Int, List<Float>>,
    ): Int {
        if (typeCount <= 0) return 0
        if (typeCount > 8) return max(loopMin, (totalRandomTarget + 9) / 10)
        val ratioList = ratios[typeCount] ?: return max(3, totalRandomTarget / typeCount)
        val minimum = max(3, loopMin - index)
        val share = (totalRandomTarget * ratioList[min(index, ratioList.lastIndex)] + 0.5f).toInt()
        return max(minimum, share)
    }
}
