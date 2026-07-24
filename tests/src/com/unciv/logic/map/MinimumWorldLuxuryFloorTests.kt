package com.unciv.logic.map

import com.unciv.logic.map.mapgenerator.MapResourceSetting
import com.unciv.logic.map.mapgenerator.resourceplacement.LuxuryResourcePlacementLogic
import com.unciv.models.ruleset.Ruleset
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spot-checks optional ModConstants world luxury floor resolution. */
class MinimumWorldLuxuryFloorTests {

    @Test
    fun zeroConstantsDisableFloor() {
        val ruleset = Ruleset()
        assertEquals(
            0,
            LuxuryResourcePlacementLogic.resolveMinimumWorldLuxuryFloor(
                ruleset, MapSize.Large, MapResourceSetting.default
            )
        )
    }

    @Test
    fun largeDefaultUsesConfiguredFloor() {
        val ruleset = Ruleset()
        ruleset.modOptions.constants.minimumWorldLuxuriesLarge = 88
        assertEquals(
            88,
            LuxuryResourcePlacementLogic.resolveMinimumWorldLuxuryFloor(
                ruleset, MapSize.Large, MapResourceSetting.default
            )
        )
    }

    @Test
    fun abundantScalesByRandomLuxuriesPercent() {
        val ruleset = Ruleset()
        ruleset.modOptions.constants.minimumWorldLuxuriesLarge = 88
        // Abundant.randomLuxuriesPercent = 133 → 88 * 133 / 100 = 117
        assertEquals(
            117,
            LuxuryResourcePlacementLogic.resolveMinimumWorldLuxuryFloor(
                ruleset, MapSize.Large, MapResourceSetting.abundant
            )
        )
    }

    @Test
    fun customRadiusMapsToNextSmallerPredefined() {
        val ruleset = Ruleset()
        ruleset.modOptions.constants.minimumWorldLuxuriesLarge = 88
        ruleset.modOptions.constants.minimumWorldLuxuriesHuge = 112
        // radius 35 → Large (30), not Huge (40)
        assertEquals(
            88,
            LuxuryResourcePlacementLogic.resolveMinimumWorldLuxuryFloor(
                ruleset, MapSize(35), MapResourceSetting.default
            )
        )
    }
}
