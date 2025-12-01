package com.unciv.uniques

import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.civilization.managers.TurnManager
import com.unciv.logic.map.HexCoord
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(GdxTestRunner::class)
class TimedUniquesTests {
    private val game = TestGame().apply { makeHexagonalMap(2) }
    private val civInfo = game.addCiv()
    private val policy =
            game.createPolicy("[+42]% Strength <when attacking> <for [Military] units> <for [1] turns>")
    private val enemy = game.addCiv()
    private val attacker =
            MapUnitCombatant(game.addUnit("Warrior", civInfo, game.getTile(HexCoord.Zero)))
    private val defender =
            MapUnitCombatant(game.addUnit("Warrior", enemy, game.getTile(1,0)))

    @Test
    fun testConditionalTimedUniqueIsTriggerable() {
        val unique = policy.uniqueObjects.first{ it.type == UniqueType.Strength }
        Assert.assertTrue("Unique with timed conditional must be triggerable", unique.isTriggerable)
    }

    @Test
    fun testConditionalTimedUniqueStrength() {
        civInfo.policies.adopt(policy, true)
        val modifiers = BattleDamage.getAttackModifiers(attacker, defender, attacker.getTile())
        Assert.assertTrue("Timed Strength should work right after triggering", modifiers.sumValues() == 42)
    }

    @Test
    fun testConditionalTimedUniqueExpires() {
        civInfo.policies.adopt(policy, true)
        // For endTurn to do the part we need, the civ must be alive - have a city or unit,
        // and right now that attacker is not in the civ's unit list
        civInfo.units.addUnit(attacker.unit, false)
        TurnManager(civInfo).endTurn()
        val modifiers = BattleDamage.getAttackModifiers(attacker, defender, attacker.getTile())
        Assert.assertTrue("Timed Strength should no longer work after endTurn", modifiers.sumValues() == 0)
    }
}
