package com.unciv.logic.map

import com.unciv.logic.map.mapgenerator.MapResourceSetting
import com.unciv.logic.map.mapgenerator.mapregions.MapRegions
import com.unciv.logic.map.mapgenerator.resourceplacement.Civ5LuxuryTargetNumbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Civ5LuxuryTargetNumbersTests {

    @Test
    fun hugeAbundantRegionalTargetMatchesCiv5Table() {
        // Civ5 Huge table for 10 civs is 7, Abundant +2 → 9
        val target = Civ5LuxuryTargetNumbers.regionalTarget(
            MapSize.Huge,
            civCount = 10,
            resourceSetting = MapResourceSetting.abundant,
        )
        assertEquals(9, target)
    }

    @Test
    fun mediumDefaultRegionalTargetMatchesCiv5StandardTable() {
        // Civ5 Standard / Unciv Medium, 8 civs → 6
        val target = Civ5LuxuryTargetNumbers.regionalTarget(
            MapSize.Medium,
            civCount = 8,
            resourceSetting = MapResourceSetting.default,
        )
        assertEquals(6, target)
    }

    @Test
    fun regionalPercentModifierScalesTarget() {
        val base = Civ5LuxuryTargetNumbers.regionalTarget(
            MapSize.Huge,
            civCount = 10,
            resourceSetting = MapResourceSetting.abundant,
            percentModifier = 1f,
        )
        val boosted = Civ5LuxuryTargetNumbers.regionalTarget(
            MapSize.Huge,
            civCount = 10,
            resourceSetting = MapResourceSetting.abundant,
            percentModifier = 1.5f,
        )
        assertEquals((base * 1.5f + 0.5f).toInt().coerceAtLeast(1), boosted)
    }

    @Test
    fun hugeAbundantWorldTargetIs128() {
        val (total, loopMin) = Civ5LuxuryTargetNumbers.worldTarget(
            MapSize.Huge,
            MapResourceSetting.abundant,
        )
        assertEquals(128, total)
        assertEquals(6, loopMin)
    }

    @Test
    fun randomTopUpCompensatesShortfall() {
        // World 128, already placed 100, variance 5 → need 33
        assertEquals(
            33,
            Civ5LuxuryTargetNumbers.randomTopUpTarget(
                worldTotalTarget = 128,
                alreadyPlaced = 100,
                extraVariance = 5,
            )
        )
        assertEquals(
            0,
            Civ5LuxuryTargetNumbers.randomTopUpTarget(
                worldTotalTarget = 80,
                alreadyPlaced = 90,
                extraVariance = 0,
            )
        )
    }

    @Test
    fun randomTypeShareUsesRatiosWhenAtMostEightTypes() {
        val total = 40
        val first = Civ5LuxuryTargetNumbers.targetForRandomLuxuryType(
            index = 0,
            typeCount = 4,
            totalRandomTarget = total,
            loopMin = 5,
            ratios = MapRegions.randomLuxuryRatios,
        )
        // ratio 0.35 → 14, min max(3, 5-0)=5 → 14
        assertEquals(14, first)
        assertTrue(first >= 5)
    }

    @Test
    fun bonusPercentModifierIncreasesDensity() {
        val base = Civ5LuxuryTargetNumbers.effectiveBonusFrequencyMultiplier(
            MapResourceSetting.default, percentModifier = 1f
        )
        val denser = Civ5LuxuryTargetNumbers.effectiveBonusFrequencyMultiplier(
            MapResourceSetting.default, percentModifier = 1.5f
        )
        assertEquals(1f, base)
        assertTrue(denser < base)
        assertEquals(1f / 1.5f, denser, 0.0001f)
    }

    @Test
    fun civ5UniqueDoesNotOverrideUncivBonusFrequency() {
        // Civ5 absolute bonus_multiplier must not be used; Unciv MapResources stays baseline.
        assertEquals(
            MapResourceSetting.abundant.bonusFrequencyMultiplier,
            Civ5LuxuryTargetNumbers.effectiveBonusFrequencyMultiplier(MapResourceSetting.abundant)
        )
        assertEquals(
            MapResourceSetting.default.bonusFrequencyMultiplier,
            Civ5LuxuryTargetNumbers.effectiveBonusFrequencyMultiplier(MapResourceSetting.default)
        )
    }
}
