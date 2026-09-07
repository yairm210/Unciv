package com.unciv.logic.civilization

import com.unciv.Constants
import com.unciv.logic.automation.civilization.NextTurnAutomation
import com.unciv.logic.map.HexCoord
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.ui.screens.worldscreen.status.NextTurnAction
import com.unciv.ui.screens.worldscreen.status.NextTurnButton
import com.unciv.ui.screens.worldscreen.unit.AutoPlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@RunWith(BaseTestRunner::class)
class GreatPersonSelectionTest {
    @Test
    fun `normal choice grants the civilization equivalent in both base rulesets`() {
        for (baseRuleset in BaseRuleset.entries) {
            val scenario = createScenario(baseRuleset, "Mongolia")
            val manager = scenario.civ.greatPeople
            manager.freeGreatPeople = 1
            manager.longCountGPPool = hashSetOf("Great Scientist", "stored value")
            val originalPool = manager.longCountGPPool.toSet()

            val options = manager.getFreeGreatPersonOptions()

            assertTrue("${baseRuleset.fullName} should offer the Khan", options.any { it.name == "Khan" })
            assertFalse(options.any { it.name == "Great General" })
            assertNull(manager.chooseFreeGreatPerson("Great General"))
            assertEquals(1, manager.freeGreatPeople)

            val placed = manager.chooseFreeGreatPerson("Khan")

            assertNotNull(placed)
            assertEquals("Khan", placed!!.name)
            assertSame(scenario.civ, placed.civ)
            assertEquals(scenario.civ.civID, placed.owner)
            assertTrue(scenario.civ.units.getCivUnits().any { it === placed })
            assertSame(placed, placed.getTile().civilianUnit)
            assertSame(placed.getTile(), scenario.game.getTile(placed.getTile().position))
            assertEquals(0, manager.freeGreatPeople)
            assertEquals(0, manager.mayaLimitedFreeGP)
            assertEquals(originalPool, manager.longCountGPPool)

            val liveUnits = scenario.civ.units.getCivUnits().toList()
            assertNull(manager.chooseFreeGreatPerson("Great Scientist"))
            assertEquals(liveUnits, scenario.civ.units.getCivUnits().toList())
        }
    }

    @Test
    fun `Maya choice uses only the current pool and consumes it after placement`() {
        val scenario = createScenario(BaseRuleset.Civ_V_GnK, "The Maya")
        val manager = scenario.civ.greatPeople
        manager.freeGreatPeople = 2
        manager.mayaLimitedFreeGP = 1
        manager.longCountGPPool = hashSetOf("Great Scientist", "stale choice")

        assertEquals(
            setOf("Great Scientist"),
            manager.getFreeGreatPersonOptions().map { it.name }.toSet(),
        )

        val unitsBeforeRejectedChoice = scenario.civ.units.getCivUnits().toList()
        assertNull(manager.chooseFreeGreatPerson("Great Engineer"))
        assertEquals(2, manager.freeGreatPeople)
        assertEquals(1, manager.mayaLimitedFreeGP)
        assertEquals(setOf("Great Scientist", "stale choice"), manager.longCountGPPool)
        assertEquals(unitsBeforeRejectedChoice, scenario.civ.units.getCivUnits().toList())

        val staleOptions = manager.getFreeGreatPersonOptions()
        manager.longCountGPPool.remove("Great Scientist")
        assertTrue(staleOptions.any { it.name == "Great Scientist" })
        assertNull(manager.chooseFreeGreatPerson("Great Scientist"))
        assertEquals(2, manager.freeGreatPeople)
        assertEquals(1, manager.mayaLimitedFreeGP)
        manager.longCountGPPool.add("Great Scientist")

        val placed = manager.chooseFreeGreatPerson("Great Scientist")

        assertNotNull(placed)
        assertEquals("Great Scientist", placed!!.name)
        assertEquals(1, manager.freeGreatPeople)
        assertEquals(0, manager.mayaLimitedFreeGP)
        assertEquals(setOf("stale choice"), manager.longCountGPPool)
        assertTrue(manager.getFreeGreatPersonOptions().any { it.name == "Great Engineer" })
    }

    @Test
    fun `invalid exhausted stale and cityless choices preserve state`() {
        val scenario = createScenario(BaseRuleset.Civ_V_GnK, "Rome")
        val manager = scenario.civ.greatPeople
        manager.freeGreatPeople = 1
        manager.longCountGPPool = hashSetOf("stored value")
        val originalPool = manager.longCountGPPool.toSet()
        val originalNotifications = scenario.civ.notifications.toList()
        val originalUnits = scenario.civ.units.getCivUnits().toList()

        assertNull(manager.chooseFreeGreatPerson("does not exist"))
        assertNull(manager.chooseFreeGreatPerson("Warrior"))
        assertEquals(1, manager.freeGreatPeople)
        assertEquals(originalPool, manager.longCountGPPool)
        assertEquals(originalNotifications, scenario.civ.notifications)
        assertEquals(originalUnits, scenario.civ.units.getCivUnits().toList())

        val staleName = manager.getFreeGreatPersonOptions().first().name
        manager.freeGreatPeople = 0
        assertTrue(manager.getFreeGreatPersonOptions().isEmpty())
        assertNull(manager.chooseFreeGreatPerson(staleName))
        assertEquals(0, manager.freeGreatPeople)
        assertEquals(originalUnits, scenario.civ.units.getCivUnits().toList())

        manager.freeGreatPeople = -1
        assertTrue(manager.getFreeGreatPersonOptions().isEmpty())
        assertNull(manager.chooseFreeGreatPerson("Great Scientist"))
        assertEquals(-1, manager.freeGreatPeople)

        val cityless = createScenario(BaseRuleset.Civ_V_GnK, "Rome", addCity = false)
        cityless.civ.greatPeople.freeGreatPeople = 1
        assertTrue(cityless.civ.greatPeople.getFreeGreatPersonOptions().isEmpty())
        assertNull(cityless.civ.greatPeople.chooseFreeGreatPerson("Great Scientist"))
        assertEquals(1, cityless.civ.greatPeople.freeGreatPeople)
        assertTrue(cityless.civ.units.getCivUnits().none())
    }

    @Test
    fun `blocked placement preserves counters pool live units and notifications`() {
        val game = TestGame()
        val civ = game.addCiv(isPlayer = true)
        val city = game.addCity(civ, game.getTile(HexCoord.Zero))
        val blocker = game.addUnit("Worker", civ, city.getCenterTile())
        val manager = civ.greatPeople
        manager.freeGreatPeople = 1
        manager.mayaLimitedFreeGP = 1
        manager.longCountGPPool = hashSetOf("Great Scientist")
        assertEquals(setOf("Great Scientist"), manager.getFreeGreatPersonOptions().map { it.name }.toSet())
        val originalUnits = civ.units.getCivUnits().toList()
        val originalMapUnits = game.tileMap.tileList.flatMap { it.getUnits().toList() }
        val originalNotifications = civ.notifications.toList()

        val placed = manager.chooseFreeGreatPerson("Great Scientist")

        assertNull(placed)
        assertEquals(1, manager.freeGreatPeople)
        assertEquals(1, manager.mayaLimitedFreeGP)
        assertEquals(setOf("Great Scientist"), manager.longCountGPPool)
        assertEquals(originalUnits, civ.units.getCivUnits().toList())
        assertEquals(originalMapUnits, game.tileMap.tileList.flatMap { it.getUnits().toList() })
        assertSame(blocker, city.getCenterTile().civilianUnit)
        assertEquals(originalNotifications, civ.notifications)
    }

    @Test
    fun `automation commits its preferred available great person`() {
        val scenario = createScenario(BaseRuleset.Civ_V_GnK, "Rome")
        val manager = scenario.civ.greatPeople
        manager.freeGreatPeople = 1

        NextTurnAutomation.chooseGreatPerson(scenario.civ)

        assertEquals(0, manager.freeGreatPeople)
        assertTrue(scenario.civ.units.getCivGreatPeople().any { it.name == "Great Scientist" })
    }

    @Test
    fun `automation retains a pick when its chosen unit cannot be placed`() {
        val game = TestGame()
        val civ = game.addCiv()
        val city = game.addCity(civ, game.getTile(HexCoord.Zero))
        val blocker = game.addUnit("Worker", civ, city.getCenterTile())
        civ.greatPeople.freeGreatPeople = 1
        val originalUnits = civ.units.getCivUnits().toList()

        NextTurnAutomation.chooseGreatPerson(civ)

        assertEquals(1, civ.greatPeople.freeGreatPeople)
        assertEquals(originalUnits, civ.units.getCivUnits().toList())
        assertSame(blocker, city.getCenterTile().civilianUnit)
    }

    @Test
    fun `deferred pending choice remains the next turn action and reopens its picker`() {
        val scenario = createScenario(BaseRuleset.Civ_V_GnK, "Rome")
        scenario.civ.greatPeople.freeGreatPeople = 1
        val autoPlay = mock(AutoPlay::class.java)
        var pickerOpenCalls = 0
        val worldScreen = mock(WorldScreen::class.java) { invocation ->
            when {
                invocation.method.name.startsWith("hasPendingFreeGreatPerson") ->
                    scenario.civ.greatPeople.freeGreatPeople > 0
                invocation.method.name.startsWith("openGreatPersonPicker") -> {
                    pickerOpenCalls++
                    null
                }
                else -> Answers.RETURNS_DEFAULTS.answer(invocation)
            }
        }
        setField(worldScreen, "viewingCiv", scenario.civ)
        setField(worldScreen, "deferFreeGreatPersonPicker", true)
        `when`(worldScreen.autoPlay).thenReturn(autoPlay)
        `when`(worldScreen.isPlayersTurn).thenReturn(true)
        val nextTurnButton = mock(NextTurnButton::class.java)
        val selector = NextTurnButton::class.java.getDeclaredMethod(
            "getNextTurnAction",
            WorldScreen::class.java,
        ).apply { isAccessible = true }

        val selectedAction = selector.invoke(nextTurnButton, worldScreen)

        assertEquals(NextTurnAction.PickGreatPerson, selectedAction)
        assertTrue(NextTurnAction.PickGreatPerson.isChoice(worldScreen))
        NextTurnAction.PickGreatPerson.action(worldScreen)
        assertEquals(1, pickerOpenCalls)

        scenario.civ.greatPeople.freeGreatPeople = 0
        assertFalse(NextTurnAction.PickGreatPerson.isChoice(worldScreen))
    }

    @Test
    fun `option query follows settings and returns independent sets without mutation`() {
        val game = TestGame()
        game.makeHexagonalMap(1, Constants.grassland)
        val civ = game.addCiv()
        game.addCity(civ, game.getTile(HexCoord.Zero))
        val conditionalGreatPerson = game.createBaseUnit(
            "Civilian",
            "Great Person - [Test]",
            "Only available <when espionage is enabled>",
        )
        val manager = civ.greatPeople
        manager.freeGreatPeople = 1
        manager.longCountGPPool = hashSetOf("stored value")

        assertFalse(manager.getFreeGreatPersonOptions().contains(conditionalGreatPerson))
        game.gameInfo.gameParameters.espionageEnabled = true
        val unitsBefore = civ.units.getCivUnits().toList()
        val notificationsBefore = civ.notifications.toList()
        val options = manager.getFreeGreatPersonOptions()
        val repeatedOptions = manager.getFreeGreatPersonOptions()

        assertTrue(options.contains(conditionalGreatPerson))
        assertEquals(options, repeatedOptions)
        assertNotSame(options, repeatedOptions)
        runCatching {
            @Suppress("UNCHECKED_CAST")
            (options as MutableSet<Any?>).clear()
        }
        assertTrue(manager.getFreeGreatPersonOptions().contains(conditionalGreatPerson))
        assertEquals(1, manager.freeGreatPeople)
        assertEquals(0, manager.mayaLimitedFreeGP)
        assertEquals(setOf("stored value"), manager.longCountGPPool)
        assertEquals(unitsBefore, civ.units.getCivUnits().toList())
        assertEquals(notificationsBefore, civ.notifications)
    }

    private fun createScenario(
        baseRuleset: BaseRuleset,
        nationName: String,
        addCity: Boolean = true,
    ): Scenario {
        val game = TestGame()
        game.makeHexagonalMap(1, Constants.grassland)
        val ruleset = when (baseRuleset) {
            BaseRuleset.Civ_V_GnK -> game.ruleset
            BaseRuleset.Civ_V_Vanilla -> RulesetCache[baseRuleset.fullName]!!.clone()
        }

        game.gameInfo.ruleset = ruleset
        game.gameInfo.gameParameters.baseRuleset = baseRuleset.fullName
        game.tileMap.ruleset = ruleset
        for (unit in ruleset.units.values) unit.setRuleset(ruleset)
        for (building in ruleset.buildings.values) building.ruleset = ruleset
        for (tile in game.tileMap.tileList) tile.setTerrainTransients()
        game.gameInfo.setGlobalTransients()

        val civ = Civilization(ruleset.nations.getValue(nationName))
        civ.gameInfo = game.gameInfo
        civ.cache.updateState()
        game.gameInfo.civilizations.add(civ)
        civ.setTransients()
        civ.tech.addTechnology(ruleset.technologies.values.minBy { it.era() }.name)
        if (addCity) game.addCity(civ, game.getTile(HexCoord.Zero))
        return Scenario(game, civ)
    }

    private data class Scenario(
        val game: TestGame,
        val civ: Civilization,
    )

    private fun setField(target: WorldScreen, name: String, value: Any) {
        WorldScreen::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(target, value)
        }
    }
}
