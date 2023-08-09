package com.unciv.logic.civilization

import com.unciv.testing.GdxTestRunner
import com.unciv.uniques.TestGame
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class DiplomacyManagerTests {

    private val testGame = TestGame()

    fun addCiv() = testGame.addCiv().apply { testGame.addUnit("Warrior", this@apply, null) }
    // We need to add units so they are not considered defeated, since defeated civs are filtered out of knowncivs
    private val a = addCiv()
    private val b = addCiv()
    private val c = addCiv()
    private val d = addCiv()


    private fun meet(civilization: Civilization, otherCivilization: Civilization){
        civilization.diplomacyFunctions.makeCivilizationsMeet(otherCivilization)
    }

    @Test
    fun `getCommonKnownCivs does not include either DiplomacyManagers's civs`() {
        meet(a, b)
        val commonKnownCivs = a.getDiplomacyManager(b).getCommonKnownCivs()

        Assert.assertTrue(a !in commonKnownCivs)
        Assert.assertTrue(b !in commonKnownCivs)
    }

    @Test
    fun `getCommonKnownCivs includes civs met by both civs`() {
        meet(a,b)
        meet(b,c)
        meet(c,a)
        val commonKnownCivs = a.getDiplomacyManager(b).getCommonKnownCivs()

        Assert.assertTrue(c in commonKnownCivs)
    }

    @Test
    fun `getCommonKnownCivs does not include civs met by only one civ`() {
        meet(a,b)
        meet(a,c)
        val commonKnownCivs = a.getDiplomacyManager(b).getCommonKnownCivs()

        Assert.assertTrue(c !in commonKnownCivs)
    }

    @Test
    fun getCommonKnownCivsIsEqualForMirroredDiplomacyManagers() {
        meet(a,b)
        meet(a,c)
        meet(b,c)
        meet(a,d)
        meet(b,d)

        Assert.assertEquals(
            a.getDiplomacyManager(b).getCommonKnownCivs(),
            b.getDiplomacyManager(a).getCommonKnownCivs()
        )
    }

}
