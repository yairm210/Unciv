package com.unciv.ui.screens.pickerscreens

import com.unciv.logic.map.HexCoord
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class ImprovementPickerScreenTests {

    private val testGame = TestGame()

    @Before
    fun setUp() {
        testGame.makeHexagonalMap(2, baseTerrain = "Grassland")
    }

    @Test
    fun plantationOnJungleWithoutBronzeWorkingIsNotRemoveFirstQueueable() {
        val civ = testGame.addCiv(isPlayer = true)
        civ.tech.addTechnology("Calendar")
        val city = testGame.addCity(civ, testGame.getTile(0, 0))
        val tile = testGame.setTileTerrainAndFeatures(HexCoord(1, 1), "Grassland", "Jungle")
        tile.setTileResource("Bananas")
        tile.setOwningCity(city)
        val worker = testGame.addUnit("Worker", civ, tile)
        testGame.gameInfo.currentPlayerCiv = civ

        val plantation = testGame.ruleset.tileImprovements["Plantation"]!!

        assertFalse(
            ImprovementPickerScreen.isRemoveFirstQueueableForTests(tile, worker, plantation)!!
        )
    }

    @Test
    fun plantationOnJungleWithBronzeWorkingIsRemoveFirstQueueable() {
        val civ = testGame.addCiv(isPlayer = true)
        civ.tech.addTechnology("Calendar")
        civ.tech.addTechnology("Bronze Working")
        val city = testGame.addCity(civ, testGame.getTile(0, 0))
        val tile = testGame.setTileTerrainAndFeatures(HexCoord(1, 1), "Grassland", "Jungle")
        tile.setTileResource("Bananas")
        tile.setOwningCity(city)
        val worker = testGame.addUnit("Worker", civ, tile)
        testGame.gameInfo.currentPlayerCiv = civ

        val plantation = testGame.ruleset.tileImprovements["Plantation"]!!

        assertTrue(
            ImprovementPickerScreen.isRemoveFirstQueueableForTests(tile, worker, plantation)!!
        )
    }
}
