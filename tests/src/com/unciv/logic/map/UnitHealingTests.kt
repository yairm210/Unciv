package com.unciv.logic.map

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class UnitHealingTests {
    private lateinit var civInfo: Civilization
    private var testGame = TestGame()

    @Before
    fun initTheWorld() {
        UncivGame.Current.settings.useAStarPathfinding = false
        testGame.makeHexagonalMap(2)
        civInfo = testGame.addCiv()
    }

    private fun addAirUnit(vararg uniques: String) =
        testGame.addUnit(testGame.createBaseUnit("Fighter", *uniques).name, civInfo, testGame.tileMap[0, 0])

    @Test
    fun airUnitWithHealsOutsideFriendlyTerritoryHealsOnWaterOutsideTerritory() {
        val oceanTile = testGame.tileMap[1, 0]
        oceanTile.baseTerrain = Constants.ocean
        oceanTile.setTerrainFeatures(listOf())
        oceanTile.setTransients()

        val airUnit = addAirUnit("May heal outside of friendly territory")
        assertEquals(10, airUnit.healingAmountForTile(oceanTile))
    }

    @Test
    fun airUnitWithoutHealsOutsideFriendlyTerritoryDoesNotHealOnWaterOutsideTerritory() {
        val oceanTile = testGame.tileMap[1, 0]
        oceanTile.baseTerrain = Constants.ocean
        oceanTile.setTerrainFeatures(listOf())
        oceanTile.setTransients()

        val airUnit = addAirUnit()
        assertEquals(0, airUnit.healingAmountForTile(oceanTile))
    }
}
