package com.unciv.uniques

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(GdxTestRunner::class)
class TriggeredUniquesTests {
    private val game = TestGame().apply { makeHexagonalMap(2) }
    private val civInfo = game.addCiv()
    private val policy =
            game.createPolicy("[+42]% Strength <when attacking> <for [Military] units> <for [1] turns>")
    private val enemy = game.addCiv()
    private val attacker =
            MapUnitCombatant(game.addUnit("Warrior", civInfo, game.setTileFeatures(Vector2.Zero)))
    private val defender =
            MapUnitCombatant(game.addUnit("Warrior", enemy, game.setTileFeatures(Vector2(1f, 0f))))

    @Test
    fun testConditionalTimedUniqueIsTriggerable() {
        val unique = policy.getMatchingUniques(UniqueType.Strength, StateForConditionals.IgnoreConditionals).firstOrNull()
        Assert.assertTrue("Unique with timed conditional must be triggerable", unique!!.isTriggerable)
    }

    @Test
    fun testConditionalTimedUniqueStrength() {
        civInfo.policies.adopt(policy, true)
        val modifiers = BattleDamage.getAttackModifiers(attacker, defender)
        Assert.assertTrue("Timed Strength should work right after triggering", modifiers.sumValues() == 42)
    }

    @Test
    fun testConditionalTimedUniqueExpires() {
        civInfo.policies.adopt(policy, true)
        civInfo.endTurn()
        val modifiers = BattleDamage.getAttackModifiers(attacker, defender)
        Assert.assertTrue("Timed Strength should no longer work after endTurn", modifiers.sumValues() == 0)
    }
}
