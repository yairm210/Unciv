package com.unciv.logic.battle

import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.managers.TurnManager
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class BattleDamageTest {
    private lateinit var attackerCiv: Civilization
    private lateinit var defenderCiv: Civilization

    private lateinit var defaultAttackerTile: Tile
    private lateinit var defaultDefenderTile: Tile

    private lateinit var defaultAttackerUnit: MapUnit
    private lateinit var defaultDefenderUnit: MapUnit

    private val testGame = TestGame()

    @Before
    fun setUp() {
        testGame.makeHexagonalMap(4)
        attackerCiv = testGame.addCiv()
        defenderCiv = testGame.addCiv()

        defaultAttackerTile = testGame.getTile(1, 1)
        defaultAttackerUnit = testGame.addUnit("Warrior", attackerCiv, defaultAttackerTile)
        defaultDefenderTile = testGame.getTile(0, 1)
        defaultDefenderUnit = testGame.addUnit("Warrior", defenderCiv, defaultDefenderTile)
    }

    @Test
    fun `should retrieve modifiers from policies`() {
        // given
        val policy = testGame.createPolicy("[+25]% Strength <when attacking> <for [Military] units>")
        attackerCiv.policies.adopt(policy, true)

        // when
        val attackModifiers = BattleDamage.getAttackModifiers(MapUnitCombatant(defaultAttackerUnit), MapUnitCombatant(defaultDefenderUnit), defaultAttackerTile)

        // then
        assertEquals(1, attackModifiers.size)
        assertEquals(25, attackModifiers.sumValues())
    }

    @Test
    fun `should retrieve modifiers from buldings`() {
        // given
        val building = testGame.createBuilding("[+15]% Strength <for [Military] units>")
        val attackerCity = testGame.addCity(attackerCiv, testGame.getTile(HexCoord.Zero))
        attackerCity.cityConstructions.addBuilding(building.name)

        // when
        val attackModifiers = BattleDamage.getAttackModifiers(MapUnitCombatant(defaultAttackerUnit), MapUnitCombatant(defaultDefenderUnit), defaultAttackerTile)

        // then
        assertEquals(1, attackModifiers.size)
        assertEquals(15, attackModifiers.sumValues())
    }

    @Test
    fun `should retrieve modifiers from national abilities`() {
        // given
        val civ = testGame.addCiv("[+10]% Strength <for [All] units> <during a Golden Age>") // i.e., Persia national ability
        civ.goldenAges.enterGoldenAge(2)
        val attackerTile = testGame.getTile(HexCoord.Zero)
        val attackerUnit = testGame.addUnit("Warrior", civ, attackerTile)

        // when
        val attackModifiers = BattleDamage.getAttackModifiers(MapUnitCombatant(attackerUnit), MapUnitCombatant(defaultDefenderUnit), attackerTile)

        // then
        assertEquals(1, attackModifiers.size)
        assertEquals(10, attackModifiers.sumValues())
    }

    @Test
    fun `should retrieve modifiers from lack of strategic resource`() {
        // given
        defaultAttackerTile.militaryUnit = null // otherwise we'll also get a flanking bonus
        val attackerTile = testGame.getTile(HexCoord.Zero)
        val attackerUnit = testGame.addUnit("Horseman", attackerCiv, attackerTile)

        // when
        val attackModifiers = BattleDamage.getAttackModifiers(MapUnitCombatant(attackerUnit), MapUnitCombatant(defaultDefenderUnit), attackerTile)

        // then
        assertEquals(1, attackModifiers.size)
        assertEquals(BattleConstants.MISSING_RESOURCES_MALUS, attackModifiers.sumValues())
    }

    @Test
    fun `should retrieve attacking flank bonus modifiers`() {
        // given
        val flankingAttackerTile = testGame.getTile(HexCoord.Zero)
        testGame.addUnit("Warrior", attackerCiv, flankingAttackerTile)

        // when
        val attackModifiers = BattleDamage.getAttackModifiers(MapUnitCombatant(defaultAttackerUnit), MapUnitCombatant(defaultDefenderUnit), defaultAttackerTile)

        // then
        assertEquals(1, attackModifiers.size)
        assertEquals(BattleConstants.BASE_FLANKING_BONUS.toInt(), attackModifiers.sumValues())
    }

    @Test
    fun `should retrieve defence fortification modifiers`() {
        // given
        defaultDefenderUnit.currentMovement = 2f // base warrior max movement points
        defaultDefenderUnit.fortify()
        TurnManager(defenderCiv).endTurn()

        // when
        val defenceModifiers = BattleDamage.getDefenceModifiers(MapUnitCombatant(defaultAttackerUnit), MapUnitCombatant(defaultDefenderUnit), defaultAttackerTile)

        // then
        assertEquals(1, defenceModifiers.size)
        assertEquals(BattleConstants.FORTIFICATION_BONUS, defenceModifiers.sumValues())
    }

    @Test
    fun `should retrieve defence terrain modifiers`() {
        // given
        testGame.setTileFeatures(defaultDefenderTile.position, Constants.hill)

        // when
        val defenceModifiers = BattleDamage.getDefenceModifiers(MapUnitCombatant(defaultAttackerUnit), MapUnitCombatant(defaultDefenderUnit), defaultAttackerTile)

        // then
        assertEquals(1, defenceModifiers.size)
        assertEquals(25, defenceModifiers.sumValues())
    }

    @Test
    fun `should not retrieve defence terrain modifiers when unit doesn't get them`() {
        // given
        val defenderTile = testGame.getTile(HexCoord.Zero)
        testGame.setTileFeatures(defenderTile.position, Constants.hill)
        val defenderUnit = testGame.addDefaultMeleeUnitWithUniques(defenderCiv, defenderTile, "No defensive terrain bonus")

        // when
        val defenceModifiers = BattleDamage.getDefenceModifiers(MapUnitCombatant(defaultAttackerUnit), MapUnitCombatant(defenderUnit), defaultAttackerTile)

        // then
        assertTrue(defenceModifiers.isEmpty())
        assertEquals(0, defenceModifiers.sumValues())
    }
}
