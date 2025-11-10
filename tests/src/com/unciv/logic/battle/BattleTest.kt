package com.unciv.logic.battle

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class BattleTest {
    private lateinit var attackerCiv: Civilization
    private lateinit var defenderCiv: Civilization

    private lateinit var defaultAttackerUnit: MapUnit
    private lateinit var defaultDefenderUnit: MapUnit

    private val testGame = TestGame()

    @Before
    fun setUp() {
        testGame.makeHexagonalMap(4)
        attackerCiv = testGame.addCiv()
        defenderCiv = testGame.addCiv()

        defaultAttackerUnit = testGame.addUnit("Warrior", attackerCiv, testGame.getTile(Vector2.X))
        defaultAttackerUnit.currentMovement = 2f
        defaultDefenderUnit = testGame.addUnit("Warrior", defenderCiv, testGame.getTile(Vector2.Zero))
        defaultDefenderUnit.currentMovement = 2f
    }

    @Test
    fun `defender should withdraw from melee attack if has the unique to do so`() {
        // given
        val defenderUnit = testGame.addDefaultMeleeUnitWithUniques(attackerCiv, testGame.getTile(Vector2.Y), UniqueType.WithdrawsBeforeMeleeCombat.text)
        defenderUnit.currentMovement = 2f

        // when
        val damageDealt = Battle.attack(MapUnitCombatant(defaultAttackerUnit), MapUnitCombatant(defenderUnit))

        // thenvi
        assertEquals(0, damageDealt.attackerDealt)
        assertEquals(0, damageDealt.defenderDealt)
        assertFalse(defenderUnit.getTile().position == Vector2.Y) // defender moves away
        assertEquals(Vector2.X, defaultAttackerUnit.getTile().position)  // attacker didn't move
        assertEquals(0f, defaultAttackerUnit.currentMovement) // warriors cannot move anymore after attacking
    }

    @Test
    fun `both defender and attacker should do damage when are melee`() {
        // when
        val damageDealt = Battle.attack(MapUnitCombatant(defaultAttackerUnit), MapUnitCombatant(defaultDefenderUnit))

        // then
        assertEquals(31, damageDealt.attackerDealt)
        assertEquals(31, damageDealt.defenderDealt)
        assertEquals(69, defaultAttackerUnit.health)
        assertEquals(69, defaultDefenderUnit.health)
    }

    @Test
    fun `only attacker should do damage when he's ranged`() {
        // given
        val attackerUnit = testGame.addUnit("Archer", attackerCiv, testGame.getTile(Vector2.Y))

        // when
        val damageDealt = Battle.attack(MapUnitCombatant(attackerUnit), MapUnitCombatant(defaultDefenderUnit))

        // then
        assertEquals(28, damageDealt.attackerDealt)
        assertEquals(0, damageDealt.defenderDealt)
    }

    @Test
    fun `should move to enemy position when melee killing`() {
        // given
        defaultDefenderUnit.health = 1

        // when
        Battle.attack(MapUnitCombatant(defaultAttackerUnit), MapUnitCombatant(defaultDefenderUnit))

        // then
        assertEquals(Vector2.Zero, defaultAttackerUnit.getTile().position)
        assertTrue(defaultDefenderUnit.isDestroyed)
    }

    @Test
    fun `should stay in original position when ranged killing`() {
        // given
        val attackerUnit = testGame.addUnit("Archer", attackerCiv, testGame.getTile(Vector2.Y))
        attackerUnit.currentMovement = 2f
        defaultDefenderUnit.health = 1

        // when
        Battle.attack(MapUnitCombatant(attackerUnit), MapUnitCombatant(defaultDefenderUnit))

        // then
        assertEquals(Vector2.Y, attackerUnit.getTile().position)
        assertTrue(defaultDefenderUnit.isDestroyed)
    }

    @Test
    fun `both attacker and defender should earn XP`() {
        // when
        Battle.attack(MapUnitCombatant(defaultAttackerUnit), MapUnitCombatant(defaultDefenderUnit))

        // then
        assertEquals(5, defaultAttackerUnit.promotions.XP)
        assertEquals(4, defaultDefenderUnit.promotions.XP)
    }

    @Test
    fun `should earn XP when fighting barbarian and below XP cap`() {
        // given
        val barbarianCiv = testGame.addBarbarianCiv()
        val barbarianUnit = testGame.addUnit("Brute", barbarianCiv, testGame.getTile(Vector2.Y))

        // when
        Battle.attack(MapUnitCombatant(barbarianUnit), MapUnitCombatant(defaultDefenderUnit))

        // then
        assertEquals(5, barbarianUnit.promotions.XP)
        assertEquals(4, defaultDefenderUnit.promotions.XP)
    }

    @Test
    fun `should not earn XP when fighting barbarian if exceeding XP cap`() {
        // given
        val barbarianCiv = testGame.addBarbarianCiv()
        val barbarianUnit = testGame.addUnit("Brute", barbarianCiv, testGame.getTile(Vector2.Y))
        defaultDefenderUnit.promotions.XP = 35

        // when
        Battle.attack(MapUnitCombatant(barbarianUnit), MapUnitCombatant(defaultDefenderUnit))

        // then
        assertEquals(5, barbarianUnit.promotions.XP)
        assertEquals(35, defaultDefenderUnit.promotions.XP)
    }

    @Test
    fun `attacker should expend all movement points to attack without uniques`() {
        // when
        Battle.attack(MapUnitCombatant(defaultAttackerUnit), MapUnitCombatant(defaultDefenderUnit))

        // then
        assertEquals(0f, defaultAttackerUnit.currentMovement)
    }

    @Test
    fun `attacker should still have movement points left with 'additional attack per turn' unique`() {
        // given
        val attackerUnit = testGame.addDefaultMeleeUnitWithUniques(attackerCiv, testGame.getTile(Vector2.Y), "[1] additional attacks per turn")
        attackerUnit.currentMovement = 2f

        // when
        Battle.attack(MapUnitCombatant(attackerUnit), MapUnitCombatant(defaultDefenderUnit))

        // then
        assertEquals(1f, attackerUnit.currentMovement)
    }

    @Test
    fun `attacker should still have movement points left with 'can move after attacking' unique`() {
        // given
        val attackerUnit = testGame.addDefaultMeleeUnitWithUniques(attackerCiv, testGame.getTile(Vector2.Y), "Can move after attacking")
        attackerUnit.currentMovement = 2f

        // when
        Battle.attack(MapUnitCombatant(attackerUnit), MapUnitCombatant(defaultDefenderUnit))

        // then
        assertEquals(1f, attackerUnit.currentMovement)
    }

    @Test
    fun `should capture civilian`() {
        // given
        val defenderUnit = testGame.addUnit("Worker", defenderCiv, testGame.getTile(Vector2(2f, 0f)))

        // when
        val attack = Battle.attack(MapUnitCombatant(defaultAttackerUnit), MapUnitCombatant(defenderUnit))

        // then
        assertEquals(0, attack.attackerDealt)
        assertEquals(0, attack.defenderDealt)
        assertEquals(Vector2(2f, 0f), defaultAttackerUnit.getTile().position)
        assertEquals(attackerCiv, defaultAttackerUnit.getTile().civilianUnit!!.civ)  // captured unit
    }

    @Test
    fun `should transform settler into worker upon capture`() {
        // given
        val defenderUnit = testGame.addUnit("Settler", defenderCiv, testGame.getTile(Vector2(2f, 0f)))

        // when
        val attack = Battle.attack(MapUnitCombatant(defaultAttackerUnit), MapUnitCombatant(defenderUnit))

        // then
        assertEquals(0, attack.attackerDealt)
        assertEquals(0, attack.defenderDealt)
        assertEquals(Vector2(2f, 0f), defaultAttackerUnit.getTile().position)
        assertEquals(attackerCiv, defaultAttackerUnit.getTile().civilianUnit!!.civ)  // captured unit
        assertEquals("Worker", defaultAttackerUnit.getTile().civilianUnit!!.baseUnit.name)
    }

    @Test
    fun `should earn Great General from combat`() {
        // when
        Battle.attack(MapUnitCombatant(defaultAttackerUnit), MapUnitCombatant(defaultDefenderUnit))

        // then
        assertEquals(5, attackerCiv.greatPeople.greatGeneralPointsCounter["Great General"])
        assertEquals(4, defenderCiv.greatPeople.greatGeneralPointsCounter["Great General"])
    }

    @Test
    fun `should not earn Great General from combat against barbarians`() {
        // given
        val barbarianCiv = testGame.addBarbarianCiv()
        val barbarianUnit = testGame.addUnit("Brute", barbarianCiv, testGame.getTile(Vector2.Y))

        // when
        Battle.attack(MapUnitCombatant(defaultAttackerUnit), MapUnitCombatant(barbarianUnit))

        // then
        assertEquals(0, attackerCiv.greatPeople.greatGeneralPointsCounter["Great General"])
        assertEquals(0, barbarianCiv.greatPeople.greatGeneralPointsCounter["Great General"])
    }

    @Test
    fun `should earn more Great General Points from uniques`() {
        // given
        val attackerUnit = testGame.addDefaultMeleeUnitWithUniques(attackerCiv, testGame.getTile(Vector2.Y), "[Great General] is earned [100]% faster")

        // when
        Battle.attack(MapUnitCombatant(attackerUnit), MapUnitCombatant(defaultDefenderUnit))

        // then
        assertEquals(10, attackerCiv.greatPeople.greatGeneralPointsCounter["Great General"])
    }

    @Test
    fun `should conquer city when defeated and melee attacked`() {
        // given
        val defenderCity = testGame.addCity(defenderCiv, testGame.getTile(Vector2.Y), initialPopulation = 1)
        defenderCity.health = 1

        testGame.gameInfo.currentPlayerCiv = testGame.addCiv() // otherwise test crashes when puppetying city
        testGame.gameInfo.currentPlayer = testGame.gameInfo.currentPlayerCiv.civName

        // when
        Battle.attack(MapUnitCombatant(defaultAttackerUnit), CityCombatant(defenderCity))

        // then
        assertEquals(attackerCiv, defenderCity.civ)
        assertEquals(defenderCiv.civName, defenderCity.previousOwner)
        assertEquals(defenderCiv, defenderCity.foundingCivObject)
    }

    @Test
    fun `should not conquer city when defeated and ranged attacked`() {
        // given
        val attackerUnit = testGame.addUnit("Archer", attackerCiv, testGame.getTile(Vector2(0f, 2f)))
        val defenderCity = testGame.addCity(defenderCiv, testGame.getTile(Vector2.Y), initialPopulation = 1)
        defenderCity.health = 1

        // when
        Battle.attack(MapUnitCombatant(attackerUnit), CityCombatant(defenderCity))

        // then
        assertEquals(defenderCiv, defenderCity.civ)
        assertEquals(1, defenderCity.health)
    }

    @Test
    fun `should not conquer city when unit has 'unable to capture cities' unique`() {
        // given
        val attackerUnit = testGame.addDefaultMeleeUnitWithUniques(attackerCiv, testGame.getTile(Vector2(0f, 2f)), "Unable to capture cities")
        val defenderCity = testGame.addCity(defenderCiv, testGame.getTile(Vector2.Y), initialPopulation = 1)
        defenderCity.health = 1

        // when
        Battle.attack(MapUnitCombatant(attackerUnit), CityCombatant(defenderCity))

        // then
        assertEquals(defenderCiv, defenderCity.civ)
        assertEquals(1, defenderCity.health)
    }

    @Test
    fun `should not gain XP when attacking defeated city`() {
        // given
        val attackerUnit = testGame.addUnit("Archer", attackerCiv, testGame.getTile(Vector2(0f, 2f)))
        val defenderCity = testGame.addCity(defenderCiv, testGame.getTile(Vector2.Y), initialPopulation = 1)
        defenderCity.health = 1

        // when
        Battle.attack(MapUnitCombatant(attackerUnit), CityCombatant(defenderCity))

        // then
        assertEquals(0, attackerUnit.promotions.XP)
    }

    @Test
    fun `should earn prizes when defeating units`() {
        // given
        val attackerPolicy = testGame.createPolicy(
            "Earn [100]% of killed [Military] unit's [Strength] as [Culture]",
            "Earn [100]% of killed [Military] unit's [Strength] as [Gold]"
        )
        attackerCiv.policies.adopt(attackerPolicy, true)

        defaultDefenderUnit.health = 1

        // when
        Battle.attack(MapUnitCombatant(defaultAttackerUnit), MapUnitCombatant(defaultDefenderUnit))

        // then
        assertEquals(8, attackerCiv.gold)
        assertEquals(8, attackerCiv.policies.storedCulture)
    }

    @Test
    fun `should heal when defeating units if has unique`() {
        // given
        val attackerUnit = testGame.addDefaultRangedUnitWithUniques(attackerCiv, testGame.getTile(Vector2.Y), "Heals [10] damage if it kills a unit")
        attackerUnit.health = 90
        attackerUnit.currentMovement = 2f

        defaultDefenderUnit.health = 1

        // when
        Battle.attack(MapUnitCombatant(attackerUnit), MapUnitCombatant(defaultDefenderUnit))

        // then
        assertEquals(100, attackerUnit.health)
    }

    @Test
    fun `should declare war when nuking neutral civs`() {
        // given
        val thirdCiv = testGame.addCiv()
        testGame.addUnit("Warrior", thirdCiv, testGame.getTile(Vector2(0f, 2f)))

        testGame.addCity(attackerCiv, testGame.getTile(Vector2.Y))
        val attackerUnit = testGame.addUnit("Atomic Bomb", attackerCiv, testGame.getTile(Vector2.Y))

        attackerCiv.diplomacyFunctions.makeCivilizationsMeet(defenderCiv)
        attackerCiv.diplomacyFunctions.makeCivilizationsMeet(thirdCiv)
        assertEquals(DiplomaticStatus.Peace, attackerCiv.diplomacy[defenderCiv.civName]!!.diplomaticStatus)
        assertEquals(DiplomaticStatus.Peace, attackerCiv.diplomacy[thirdCiv.civName]!!.diplomaticStatus)

        // when
        Battle.attackOrNuke(MapUnitCombatant(attackerUnit), AttackableTile(attackerUnit.getTile(), defaultDefenderUnit.currentTile, 0f, null))

        // then
        assertEquals(DiplomaticStatus.War, attackerCiv.diplomacy[defenderCiv.civName]!!.diplomaticStatus)
        assertEquals(DiplomaticStatus.War, attackerCiv.diplomacy[thirdCiv.civName]!!.diplomaticStatus)
    }

    @Test
    fun `should give diplomacy penality for using a nuke`() {
        // given
        val thirdCiv = testGame.addCiv()
        testGame.addUnit("Warrior", thirdCiv, testGame.getTile(Vector2(0f, -3f)))  // need unit or civ is considered defeated

        testGame.addCity(attackerCiv, testGame.getTile(Vector2.Y))
        val attackerUnit = testGame.addUnit("Atomic Bomb", attackerCiv, testGame.getTile(Vector2.Y))

        attackerCiv.diplomacyFunctions.makeCivilizationsMeet(defenderCiv)
        attackerCiv.diplomacyFunctions.makeCivilizationsMeet(thirdCiv)

        // when
        Battle.attackOrNuke(MapUnitCombatant(attackerUnit), AttackableTile(attackerUnit.getTile(), defaultDefenderUnit.currentTile, 0f, null))

        // then
        assertEquals(-75f, defenderCiv.getDiplomacyManager(attackerCiv)!!.opinionOfOtherCiv()) // 50 for nuke, 25 for war declaration
        assertEquals(-55f, thirdCiv.getDiplomacyManager(attackerCiv)!!.opinionOfOtherCiv()) // 50 for nuke, 5 for warmongering
    }

    @Test
    fun `should always destroy unit directly hit by nuke`() {
        // given
        val megaWarrior = testGame.createBaseUnit("Sword").apply { strength = 1_000_000 }
        val defenderUnit = testGame.addUnit(megaWarrior.name, defenderCiv, testGame.getTile(Vector2.Y))

        testGame.addCity(attackerCiv, testGame.getTile(Vector2.Y))
        val attackerUnit = testGame.addUnit("Atomic Bomb", attackerCiv, testGame.getTile(Vector2.Y))
        attackerCiv.resourceStockpiles["Uranium"] = 1

        // when
        Battle.attackOrNuke(MapUnitCombatant(attackerUnit), AttackableTile(attackerUnit.getTile(), defenderUnit.currentTile, 0f, null))

        // then
        assertTrue(defenderUnit.isDestroyed)
    }

    @Test
    fun `should damage ALL units in blast radius`() {
        // given
        val thirdCiv = testGame.addCiv()
        val thirdCivUnit = testGame.addUnit("Warrior", thirdCiv, testGame.getTile(Vector2(0f, 2f)))
        val defenderUnit = testGame.addUnit("Warrior", defenderCiv, testGame.getTile(Vector2.Y))

        testGame.addCity(attackerCiv, testGame.getTile(Vector2.Y))
        val attackerUnit = testGame.addUnit("Atomic Bomb", attackerCiv, testGame.getTile(Vector2.Y))

        // when
        Battle.attackOrNuke(MapUnitCombatant(attackerUnit), AttackableTile(attackerUnit.getTile(), defaultDefenderUnit.currentTile, 0f, null))

        // then
        assertTrue(defenderUnit.health < 100)
        assertTrue(thirdCivUnit.health < 100)
        assertTrue(defaultAttackerUnit.health < 100)  // even attacker's own units
        assertTrue(defaultDefenderUnit.health < 100)
    }
    @Test
    fun `should kill people in city`() {
        // given
        val defenderCity = testGame.addCity(defenderCiv, testGame.getTile(Vector2(2f, 0f)), initialPopulation = 10)

        testGame.addCity(attackerCiv, testGame.getTile(Vector2.Y))
        val attackerUnit = testGame.addUnit("Atomic Bomb", attackerCiv, testGame.getTile(Vector2.Y))

        // when
        Battle.attackOrNuke(MapUnitCombatant(attackerUnit), AttackableTile(attackerUnit.getTile(), defenderCity.getCenterTile(), 0f, null))

        // then
        assertTrue(defenderCity.population.population in 3..7)  // there is some randomness in population killed
    }

    @Test
    fun `should kill fewer people in city with bomb shelter`() {
        // given
        val defenderCity = testGame.addCity(defenderCiv, testGame.getTile(Vector2(2f, 0f)), initialPopulation = 10)
        val building = testGame.createBuilding("Population loss from nuclear attacks [-100]% [in this city]")
        defenderCity.cityConstructions.addBuilding(building.name)

        testGame.addCity(attackerCiv, testGame.getTile(Vector2.Y))
        val attackerUnit = testGame.addUnit("Atomic Bomb", attackerCiv, testGame.getTile(Vector2.Y))

        // when
        Battle.attackOrNuke(MapUnitCombatant(attackerUnit), AttackableTile(attackerUnit.getTile(), defenderCity.getCenterTile(), 0f, null))

        // then
        assertEquals(10, defenderCity.population.population)
    }

    @Test
    fun `should not destroy city with nuclear missile when capital`() {
        // given
        val defenderCity = testGame.addCity(defenderCiv, testGame.getTile(Vector2(2f, 0f)), initialPopulation = 1)

        testGame.addCity(attackerCiv, testGame.getTile(Vector2.Y))
        val attackerUnit = testGame.addUnit("Nuclear Missile", attackerCiv, testGame.getTile(Vector2.Y))

        // when
        Battle.attackOrNuke(MapUnitCombatant(attackerUnit), AttackableTile(attackerUnit.getTile(), defenderCity.getCenterTile(), 0f, null))

        // then
        assertTrue(testGame.getTile(Vector2(2f, 0f)).isCityCenter())
    }

    @Test
    fun `should destroy city with nuclear missile when not capital and below population threshold`() {
        // given
        testGame.addCity(defenderCiv, testGame.getTile(Vector2(2f, 0f)), initialPopulation = 1) // capital
        val nonCapitalDefenderCity = testGame.addCity(defenderCiv, testGame.getTile(Vector2(3f, 0f)), initialPopulation = 1)

        testGame.addCity(attackerCiv, testGame.getTile(Vector2.Y))
        val attackerUnit = testGame.addUnit("Nuclear Missile", attackerCiv, testGame.getTile(Vector2.Y))

        // when
        Battle.attackOrNuke(MapUnitCombatant(attackerUnit), AttackableTile(attackerUnit.getTile(), nonCapitalDefenderCity.getCenterTile(), 0f, null))

        // then
        assertFalse(testGame.getTile(Vector2(3f, 0f)).isCityCenter())
        assertTrue(testGame.getTile(Vector2(3f, 0f)).terrainFeatures.contains("Fallout"))
    }

    @Test
    fun `should not destroy city with nuclear missile when not capital and above population threshold`() {
        // given
        testGame.addCity(defenderCiv, testGame.getTile(Vector2(2f, 0f)), initialPopulation = 1) // capital
        val nonCapitalDefenderCity = testGame.addCity(defenderCiv, testGame.getTile(Vector2(3f, 0f)), initialPopulation = 7)

        testGame.addCity(attackerCiv, testGame.getTile(Vector2.Y))
        val attackerUnit = testGame.addUnit("Nuclear Missile", attackerCiv, testGame.getTile(Vector2.Y))

        // when
        Battle.attackOrNuke(MapUnitCombatant(attackerUnit), AttackableTile(attackerUnit.getTile(), nonCapitalDefenderCity.getCenterTile(), 0f, null))

        // then
        assertTrue(testGame.getTile(Vector2(3f, 0f)).isCityCenter())
    }

    @Test
    fun `should consume nuke on usage`() {
        // given
        testGame.addCity(attackerCiv, testGame.getTile(Vector2.Y))
        val attackerUnit = testGame.addUnit("Atomic Bomb", attackerCiv, testGame.getTile(Vector2.Y))

        // when
        Battle.attackOrNuke(MapUnitCombatant(attackerUnit), AttackableTile(attackerUnit.getTile(), defaultDefenderUnit.getTile(), 0f, null))

        // then
        assertTrue(attackerUnit.isDestroyed)
    }

    
    @Test
    fun `should trigger damage triggers when ranged attacking`() {
        // given
        val unitType = testGame.createBaseUnit("Archery",
            "[This Unit] takes [5] damage <upon damaging a [Test] unit>")
        unitType.rangedStrength = 10
        val attackerUnit = testGame.addUnit(unitType.name, attackerCiv, testGame.getTile(Vector2.Y))
        attackerUnit.currentMovement = 2f
        defaultDefenderUnit.health = 1

        // when
        defaultDefenderUnit.setStatus("Test", 1)
        Battle.attack(MapUnitCombatant(attackerUnit), MapUnitCombatant(defaultDefenderUnit))

        // then
        assertEquals(95, attackerUnit.health)
        assertTrue(defaultDefenderUnit.isDestroyed)
    }
}
