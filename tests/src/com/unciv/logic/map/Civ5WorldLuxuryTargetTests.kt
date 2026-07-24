package com.unciv.logic.map

import com.unciv.logic.map.mapgenerator.MapResourceSetting
import com.unciv.logic.map.mapgenerator.resourceplacement.LuxuryResourcePlacementLogic
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spot-checks Civ5 world luxury totals behind `Civ5-style world luxury targets`. */
class Civ5WorldLuxuryTargetTests {

    @Test
    fun mediumDefaultWorldTargetMatchesCiv5Standard() {
        val (total, loopMin) = LuxuryResourcePlacementLogic.civ5WorldTarget(
            MapSize.Medium, MapResourceSetting.default
        )
        assertEquals(60, total)
        assertEquals(5, loopMin)
    }

    @Test
    fun largeDefaultWorldTargetIs88() {
        val (total, loopMin) = LuxuryResourcePlacementLogic.civ5WorldTarget(
            MapSize.Large, MapResourceSetting.default
        )
        assertEquals(88, total)
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
