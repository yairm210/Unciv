package com.unciv.logic.automation.unit

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessFiles
import com.unciv.logic.automation.Automation
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Test

class NavalDefenseOwnershipTest {
    @Test fun `foreign ships do not satisfy our naval defence requirement`() {
        Gdx.files = HeadlessFiles()
        val game = TestGame()
        game.makeHexagonalMap(6)
        for (tile in game.tileMap.values) {
            tile.baseTerrain = "Coast"
            tile.setTerrainTransients()
        }
        for (x in listOf(-3, 3)) game.setTileTerrain(game.tileMap[x, 0].position, "Grassland")
        val civ = game.addCiv()
        val foreign = game.addCiv()
        val city = game.addCity(civ, game.tileMap[-3, 0])
        game.addCity(foreign, game.tileMap[3, 0])
        civ.tech.addTechnology("Sailing", false)
        game.addUnit("Trireme", foreign, game.tileMap[1, 0])
        game.addUnit("Trireme", foreign, game.tileMap[2, 0])
        city.cityConstructions.setCurrentConstruction("Monument")

        // Without the naval-defence priority, the force shortlist must choose land.
        // This makes the regression deterministic, independent of the random type choice.
        val landUnit = game.ruleset.units["Warrior"]!!.apply { strength = 1000 }
        val ship = game.ruleset.units["Trireme"]!!
        assertEquals("Trireme", Automation.chooseMilitaryUnit(city, sequenceOf(ship, landUnit))!!.name)

        // A ship actually owned by the city owner does satisfy the requirement.
        game.addUnit("Trireme", civ, game.tileMap[-2, 0])
        assertEquals("Warrior", Automation.chooseMilitaryUnit(city, sequenceOf(ship, landUnit))!!.name)
    }
}
