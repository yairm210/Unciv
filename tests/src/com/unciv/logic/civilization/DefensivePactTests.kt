package com.unciv.logic.civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyTurnManager.nextTurn
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class DefensivePactTests {
    private val testGame = TestGame()

    fun addCiv() = testGame.addCiv().apply { testGame.addUnit("Warrior", this@apply, null) }

    private val a = addCiv()
    private val b = addCiv()
    private val c = addCiv()



    private fun meetAll() {
        a.diplomacyFunctions.makeCivilizationsMeet(b)
        a.diplomacyFunctions.makeCivilizationsMeet(c)
        b.diplomacyFunctions.makeCivilizationsMeet(c)
    }

    @Test
    fun `Civs with Defensive Pacts are called in`() {
        meetAll()
        a.getDiplomacyManager(b)!!.signDefensivePact(100)
        Assert.assertTrue(a.getDiplomacyManager(b)!!.diplomaticStatus == DiplomaticStatus.DefensivePact
            && b.getDiplomacyManager(a)!!.diplomaticStatus == DiplomaticStatus.DefensivePact)
        c.getDiplomacyManager(a)!!.declareWar()

        Assert.assertTrue(c.isAtWarWith(b) && c.isAtWarWith(a))
        Assert.assertTrue(b.isAtWarWith(c) && a.isAtWarWith(c))
        Assert.assertTrue(b.getDiplomacyManager(a)!!.diplomaticStatus == DiplomaticStatus.DefensivePact
            || b.getDiplomacyManager(a)!!.diplomaticStatus == DiplomaticStatus.DefensivePact)
    }

    @Test
    fun `Defensive Pact cancel when attacking`() {
        meetAll()
        a.getDiplomacyManager(b)!!.signDefensivePact(100)
        a.getDiplomacyManager(c)!!.declareWar()

        Assert.assertFalse(a.getDiplomacyManager(b)!!.diplomaticStatus == DiplomaticStatus.DefensivePact)
    }

    @Test
    fun `Defensive Pact timeout`() {
        meetAll()
        a.getDiplomacyManager(b)!!.signDefensivePact(1)
        Assert.assertTrue(a.getDiplomacyManager(b)!!.diplomaticStatus == DiplomaticStatus.DefensivePact)
        a.getDiplomacyManager(b)!!.nextTurn()
        b.getDiplomacyManager(a)!!.nextTurn()

        Assert.assertFalse(a.getDiplomacyManager(b)!!.diplomaticStatus == DiplomaticStatus.DefensivePact
            || b.getDiplomacyManager(a)!!.diplomaticStatus == DiplomaticStatus.DefensivePact)
    }

    @Test
    fun `Breaking Defensive Pact`() {
        meetAll()
        val abDiploManager = a.getDiplomacyManager(b)!!
        abDiploManager.signDefensivePact(100)
        abDiploManager.declareWar()
        Assert.assertTrue(abDiploManager.otherCivDiplomacy().hasModifier(DiplomaticModifiers.BetrayedDefensivePact)) // Defender should be extra mad
        Assert.assertTrue(abDiploManager.hasModifier(DiplomaticModifiers.DefensivePact)) // Aggressor should still be friendly to the defender, they did nothing wrong
        Assert.assertFalse(abDiploManager.otherCivDiplomacy().hasModifier(DiplomaticModifiers.DefensivePact)) // Defender should not be friendly to the aggressor.
    }

    @Test
    fun `Breaking Defensive Pact with friends`() {
        meetAll()
        val abDiploManager = a.getDiplomacyManager(b)!!
        val bcDiploManager = b.getDiplomacyManager(c)!!
        abDiploManager.signDefensivePact(100)
        bcDiploManager.signDeclarationOfFriendship()

        abDiploManager.declareWar()
        Assert.assertTrue(c.getDiplomacyManager(a)!!.hasModifier(DiplomaticModifiers.BetrayedDefensivePact))
    }
}
