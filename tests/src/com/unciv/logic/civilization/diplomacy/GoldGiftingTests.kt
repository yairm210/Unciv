package com.unciv.logic.civilization.diplomacy

import com.unciv.logic.civilization.diplomacy.DiplomacyTurnManager.nextTurn
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class GoldGiftingTests {

    private val testGame = TestGame()

    private val a = testGame.addCiv()
    private val b = testGame.addCiv()
    lateinit var aDiplomacy: DiplomacyManager
    lateinit var bDiplomacy: DiplomacyManager


    @Before
    fun setUp() {
        a.diplomacyFunctions.makeCivilizationsMeet(b)
        aDiplomacy = a.getDiplomacyManager(b)
        bDiplomacy = b.getDiplomacyManager(a)
    }

    @Test
    fun `Gold Gift Test` () {
        assertEquals(0, aDiplomacy.getGoldGifts())
        assertEquals(0, bDiplomacy.getGoldGifts())
        aDiplomacy.recieveGoldGifts(10)
        assertTrue(aDiplomacy.getGoldGifts() > 0)
        assertEquals(0, bDiplomacy.getGoldGifts())
    }

    @Test
    fun `Gifted Gold Disapears` () {
        aDiplomacy.recieveGoldGifts(10)
        assertTrue(aDiplomacy.getGoldGifts() > 0)
        val gold = aDiplomacy.getGoldGifts()
        aDiplomacy.nextTurn()
        val gold2 = aDiplomacy.getGoldGifts()
        assertTrue(gold > gold2)
        assertTrue(gold2 >= 0) // Gold should not be negative
        // We don't actually test if the gift has completely run out
        // since that may change in the future
    }


    @Test
    fun `A lot of Gifted Gold is reduced a little` () {
        aDiplomacy.recieveGoldGifts(1000)
        assertTrue(aDiplomacy.getGoldGifts() > 0)
        val gold = aDiplomacy.getGoldGifts()
        aDiplomacy.nextTurn()
        val gold2 = aDiplomacy.getGoldGifts()
        assertTrue(gold > gold2)
        assertTrue(gold2 >= gold * .9) // We shoulden't loose more than 10% of the value in one turn
        assertTrue(gold2 >= 0) 
    }

}

