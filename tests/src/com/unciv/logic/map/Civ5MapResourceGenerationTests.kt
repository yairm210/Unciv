package com.unciv.logic.map

import com.unciv.logic.map.mapgenerator.MapResourceSetting
import com.unciv.logic.map.mapgenerator.resourceplacement.LuxuryResourcePlacementLogic
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spot-checks Civ5 AssignStartingPlots tables behind `Civ5-style map resource generation`. */
class Civ5MapResourceGenerationTests {

    @Test
    fun hugeAbundantRegionalTargetMatchesCiv5() {
        // Civ5 Huge table for 10 civs is 7, Abundant +2 → 9
        assertEquals(
            9,
            LuxuryResourcePlacementLogic.civ5RegionalTarget(MapSize.Huge, 10, MapResourceSetting.abundant)
        )
    }

    @Test
    fun mediumDefaultWorldTargetMatchesCiv5Standard() {
        val (total, loopMin) = LuxuryResourcePlacementLogic.civ5WorldTarget(
            MapSize.Medium, MapResourceSetting.default
        )
        assertEquals(60, total)
        assertEquals(5, loopMin)
    }

    @Test
    fun hugeAbundantWorldTargetIs128() {
        val (total, loopMin) = LuxuryResourcePlacementLogic.civ5WorldTarget(
            MapSize.Huge, MapResourceSetting.abundant
        )
        assertEquals(128, total)
        assertEquals(6, loopMin)
    }
}
