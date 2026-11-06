package com.unciv.logic.battle

import com.unciv.logic.automation.unit.BattleHelper
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import com.unciv.view.GameView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A city never goes below 1 HP. It defends at full strength, however low its health: a melee unit takes the damage even when it
 *  takes the city, and only takes it if it survives. Ranged units can neither take it nor are hurt by it. */
@RunWith(BaseTestRunner::class)
class CityDefenseTests {
    private val game = TestGame()
    private lateinit var us: Civilization
    private lateinit var them: Civilization
    private lateinit var city: City
    private lateinit var cityCombatant: CityCombatant

    @Before
    fun setUp() {
        game.makeHexagonalMap(6)
        us = game.addCiv()
        them = game.addCiv()
        us.diplomacyFunctions.makeCivilizationsMeet(them)
        us.getDiplomacyManager(them)!!.declareWar()
        city = game.addCity(them, game.getTile(HexCoord.Zero))
        game.addCity(them, game.getTile(0, 5)) // so that taking this one does not eliminate the civilization
        cityCombatant = CityCombatant(city)
        city.health = 1
    }

    private fun meleeUnit(health: Int = 100): MapUnit =
        game.addDefaultMeleeUnitWithUniques(us, game.getTile(1, 0)).also { it.health = health }

    @Test
    fun `defending strength does not depend on city health`() {
        val attacker = MapUnitCombatant(meleeUnit())
        city.health = cityCombatant.getMaxHealth()
        val atFullHealth = cityCombatant.getDefendingStrength(attacker)
        city.health = 1
        assertEquals(atFullHealth, cityCombatant.getDefendingStrength(attacker))
    }

    @Test
    fun `a melee unit takes damage from a city at minimum health and still captures it`() {
        val unit = meleeUnit()

        Battle.attack(MapUnitCombatant(unit), cityCombatant)

        assertEquals("captured", us, city.civ)
        assertTrue("hurt: ${unit.health}", unit.health < 100)
        assertFalse(unit.isDestroyed)
    }

    @Test
    fun `a melee unit that does not survive the fight does not capture the city`() {
        val unit = meleeUnit(health = 5)

        Battle.attack(MapUnitCombatant(unit), cityCombatant)

        assertTrue(unit.isDestroyed)
        assertEquals(them, city.civ)
        assertEquals(1, city.health)
    }

    @Test
    fun `a ranged unit neither takes the city nor takes damage from it`() {
        val unit = game.addDefaultRangedUnitWithUniques(us, game.getTile(2, 0))

        Battle.attack(MapUnitCombatant(unit), cityCombatant)

        assertEquals(them, city.civ)
        assertEquals(1, city.health)
        assertEquals(100, unit.health)
    }

    @Test
    fun `the AI does not send a unit to die against a city at minimum health, but takes it with a healthy one`() {
        val wounded = meleeUnit(health = 5)
        BattleHelper.tryAttackNearbyEnemy(GameView(game.gameInfo, us).getMapUnitView(wounded))
        assertFalse(wounded.isDestroyed)
        assertEquals(them, city.civ)
        wounded.destroy()

        val healthy = meleeUnit()
        BattleHelper.tryAttackNearbyEnemy(GameView(game.gameInfo, us).getMapUnitView(healthy))
        assertEquals(us, city.civ)
    }

    @Test
    fun `a barbarian that survives raids the city for gold instead of capturing it`() {
        them.addGold(100)
        val barbarian = game.addUnit("Brute", game.addBarbarianCiv(), game.getTile(1, 0))

        Battle.attack(MapUnitCombatant(barbarian), cityCombatant)

        assertEquals(them, city.civ)
        assertEquals(0, them.gold)
        assertTrue(barbarian.isDestroyed)
    }

    @Test
    fun `a barbarian that does not survive the city does not raid it`() {
        them.addGold(100)
        val barbarian = game.addUnit("Brute", game.addBarbarianCiv(), game.getTile(1, 0)).also { it.health = 5 }

        Battle.attack(MapUnitCombatant(barbarian), cityCombatant)

        assertTrue(barbarian.isDestroyed)
        assertEquals(1, city.health)
        assertEquals(100, them.gold)
    }

    @Test
    fun `the damage estimate behind the attack preview is the same at any city health`() {
        val attacker = MapUnitCombatant(meleeUnit())
        fun estimate() = listOf(0f, 1f).map { BattleDamage.calculateDamageToAttacker(attacker, cityCombatant, attacker.getTile(), it) }

        val atMinimum = estimate()
        city.health = cityCombatant.getMaxHealth()

        assertEquals(estimate(), atMinimum)
        assertTrue("estimate $atMinimum", atMinimum.all { it > 0 })
    }
}
