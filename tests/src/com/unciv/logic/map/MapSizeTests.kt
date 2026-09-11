package com.unciv.logic.map

import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class MapSizeTests {

    @Test
    fun testInferPredefinedProperty() {
        fun assertEqualModifiers(predefined: MapSize.Predefined, mapSize: MapSize) {
            Assert.assertEquals(predefined.techCostMultiplier, mapSize.getTechCostMultiplier())
            Assert.assertEquals(predefined.techCostPerCityModifier, mapSize.getTechCostPerCityModifier())
            Assert.assertEquals(predefined.policyCostPerCityModifier, mapSize.getPolicyCostPerCityModifier())
        }
        
        // Non-custom map size
        for (predefined in MapSize.Predefined.entries)
            assertEqualModifiers(predefined, MapSize(predefined))
        
        // same modifiers <= Small
        assertEqualModifiers(MapSize.Predefined.Tiny, MapSize(MapSize.Predefined.Tiny.radius - 5))
        assertEqualModifiers(MapSize.Predefined.Tiny, MapSize(MapSize.Predefined.Tiny.radius + 1))
        assertEqualModifiers(MapSize.Predefined.Tiny, MapSize(MapSize.Predefined.Small))

        fun assertApproxEqualModifiers(
            mapSize: MapSize,
            techCostMultiplier: Float,
            techCostPerCityModifier: Float,
            policyCostPerCityModifier: Float
        ) {
            val delta = 0.0001f
            Assert.assertEquals(techCostMultiplier, mapSize.getTechCostMultiplier(), delta)
            Assert.assertEquals(techCostPerCityModifier, mapSize.getTechCostPerCityModifier(), delta)
            Assert.assertEquals(policyCostPerCityModifier, mapSize.getPolicyCostPerCityModifier(), delta)
        }

        /**
         * These will break if you change the [MapSize.Predefined] entries.
         * To fix, recalculate the expected [MapSize.Predefined.techCostMultiplier], [MapSize.Predefined.techCostPerCityModifier] and [MapSize.Predefined.policyCostPerCityModifier] values.
         */
        
        // slightly larger than Small
        assertApproxEqualModifiers(
            MapSize(MapSize.Predefined.Small.radius + 1),
            1.02f, 0.05f, 0.1f
        )
        // Medium and Large midpoint
        assertApproxEqualModifiers(
            MapSize(arrayOf(MapSize.Predefined.Medium.radius, MapSize.Predefined.Large.radius).average().toInt()),
            1.15f, 0.04375f, 0.0875f
        )
        // extrapolate beyond Huge
        assertApproxEqualModifiers(
            MapSize(MapSize.Predefined.Huge.radius + 10),
            1.4f, 0.0125f, 0.025f
        )
        // extreme, check never goes below 0
        assertApproxEqualModifiers(
            MapSize(100),
            1.9f, 0f, 0f
        )
    }
}
