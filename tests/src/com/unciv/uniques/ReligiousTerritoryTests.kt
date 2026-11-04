package com.unciv.uniques

import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Defender of the Faith and Just War give +20% only in the territory of cities that follow the religion of the civilization,
 *  friendly respectively enemy territory. Uses the beliefs as defined in the G&K ruleset. */
@RunWith(BaseTestRunner::class)
class ReligiousTerritoryTests {
    private val game = TestGame()
    private val filter = "in cities following our religion"
    private lateinit var us: Civilization
    private lateinit var them: Civilization
    private lateinit var ourFollowingCity: City
    private lateinit var ourOtherCity: City
    private lateinit var theirFollowingCity: City
    private lateinit var theirOtherCity: City

    @Before
    fun setUp() {
        game.makeHexagonalMap(10)
        val beliefUniques = listOf("Defender of the Faith", "Just War").flatMap { game.ruleset.beliefs[it]!!.uniques }
        us = game.addCiv(*beliefUniques.toTypedArray())
        them = game.addCiv()
        us.diplomacyFunctions.makeCivilizationsMeet(them)
        us.getDiplomacyManager(them)!!.declareWar()

        ourFollowingCity = game.addCity(us, game.getTile(0, 0))
        ourOtherCity = game.addCity(us, game.getTile(0, 5))
        theirFollowingCity = game.addCity(them, game.getTile(5, 0))
        theirOtherCity = game.addCity(them, game.getTile(-5, 0))

        val ourReligion = game.addReligion(us)
        val theirReligion = game.addReligion(them)
        ourFollowingCity.religion.addPressure(ourReligion.name, 1000)
        theirFollowingCity.religion.addPressure(ourReligion.name, 1000)
        theirOtherCity.religion.addPressure(theirReligion.name, 1000) // follows another religion
    }

    private fun tileOf(city: City): Tile =
        city.getCenterTile().neighbors.first { it.getCity() == city && it.militaryUnit == null }

    private fun meleeUnit(civ: Civilization, tile: Tile): MapUnitCombatant {
        val unit: MapUnit = game.addDefaultMeleeUnitWithUniques(civ, tile)
        return MapUnitCombatant(unit)
    }

    private fun Map<String, Int>.total() = values.sum()

    @Test
    fun `the filter needs a city that follows the religion founded by the observing civilization`() {
        assertTrue(tileOf(ourFollowingCity).matchesFilter(filter, us))
        assertTrue(tileOf(theirFollowingCity).matchesFilter(filter, us))
        assertFalse("no majority religion", tileOf(ourOtherCity).matchesFilter(filter, us))
        assertFalse("another religion", tileOf(theirOtherCity).matchesFilter(filter, us))
        assertFalse("a civilization without a founded religion", tileOf(theirFollowingCity).matchesFilter(filter, game.addCiv()))
        assertFalse("unowned tile", game.getTile(0, 9).matchesFilter(filter, us))
    }

    @Test
    fun `Defender of the Faith applies in friendly territory of cities following our religion`() {
        val attacker = meleeUnit(them, tileOf(ourFollowingCity).neighbors.first { it.militaryUnit == null && it.getCity() == null })
        val defender = meleeUnit(us, tileOf(ourFollowingCity))
        assertEquals(20, BattleDamage.getDefenceModifiers(attacker, defender, attacker.getTile()).total())
    }

    @Test
    fun `Defender of the Faith does not apply in our territory where the city does not follow our religion`() {
        val attacker = meleeUnit(them, tileOf(ourOtherCity).neighbors.first { it.militaryUnit == null && it.getCity() == null })
        val defender = meleeUnit(us, tileOf(ourOtherCity))
        assertEquals(0, BattleDamage.getDefenceModifiers(attacker, defender, attacker.getTile()).total())
    }

    @Test
    fun `Just War applies when attacking into enemy territory of cities following our religion`() {
        val attacker = meleeUnit(us, tileOf(ourFollowingCity))
        val defender = meleeUnit(them, tileOf(theirFollowingCity))
        assertEquals(20, BattleDamage.getAttackModifiers(attacker, defender, attacker.getTile()).total())
    }

    @Test
    fun `Just War does not apply in enemy territory where the city follows another religion`() {
        val attacker = meleeUnit(us, tileOf(ourFollowingCity))
        val defender = meleeUnit(them, tileOf(theirOtherCity))
        assertEquals(0, BattleDamage.getAttackModifiers(attacker, defender, attacker.getTile()).total())
    }
}
