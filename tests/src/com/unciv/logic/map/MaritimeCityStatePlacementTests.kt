package com.unciv.logic.map

import com.unciv.logic.map.mapgenerator.mapregions.MinorCivPlacer
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class MaritimeCityStatePlacementTests {

    @Test
    fun maritimeTypePrefersCoast() {
        val game = TestGame()
        val maritime = game.addCiv(cityStateType = "Maritime")
        assertTrue(MinorCivPlacer.prefersCoastalStart(maritime))
    }

    @Test
    fun coastStartBiasPrefersCoast() {
        val game = TestGame()
        val cultured = game.addCiv(cityStateType = "Cultured")
        cultured.nation.startBias.add("Coast")
        assertTrue(MinorCivPlacer.prefersCoastalStart(cultured))
    }

    @Test
    fun militaristicWithoutCoastBiasDoesNotPreferCoast() {
        val game = TestGame()
        val militaristic = game.addCiv(cityStateType = "Militaristic")
        assertFalse(MinorCivPlacer.prefersCoastalStart(militaristic))
    }
}