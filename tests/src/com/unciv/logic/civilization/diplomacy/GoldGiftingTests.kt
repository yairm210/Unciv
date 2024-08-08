package com.unciv.logic.civilization.diplomacy

import com.unciv.logic.civilization.diplomacy.DiplomacyTurnManager.nextTurn
import com.unciv.logic.trade.TradeEvaluation
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeOfferType
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
        aDiplomacy = a.getDiplomacyManager(b)!!
        bDiplomacy = b.getDiplomacyManager(a)!!
        a.gameInfo.ruleset.modOptions.constants.goldGiftMultiplier = 1f
        a.gameInfo.ruleset.modOptions.constants.goldGiftTradeMultiplier = 1f
        a.gameInfo.ruleset.modOptions.constants.goldGiftDegradationMultiplier = 1f
    }

    @Test
    fun `Gold Gift Recieve Test` () {
        assertEquals(0, aDiplomacy.getGoldGifts())
        assertEquals(0, bDiplomacy.getGoldGifts())
        aDiplomacy.recieveGoldGifts(10)
        assertTrue(aDiplomacy.getGoldGifts() > 0)
        assertEquals(0, bDiplomacy.getGoldGifts())
    }

    @Test
    fun `Gold Gift Test` () {
        assertEquals(0, aDiplomacy.getGoldGifts())
        assertEquals(0, bDiplomacy.getGoldGifts())
        aDiplomacy.giftGold(10, true)
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
    fun `Gifted Gold is reduced less than 10 percent` () {
        aDiplomacy.recieveGoldGifts(1000)
        assertTrue(aDiplomacy.getGoldGifts() > 0)
        val gold = aDiplomacy.getGoldGifts()
        aDiplomacy.nextTurn()
        val gold2 = aDiplomacy.getGoldGifts()
        assertTrue(gold > gold2)
        assertTrue(gold2 >= gold * .9) // We shoulden't loose more than 10% of the value in one turn
        assertTrue(gold2 >= 0)
    }

    @Test
    fun `Gold gifted is lost during war` () {
        aDiplomacy.recieveGoldGifts(1000)
        bDiplomacy.recieveGoldGifts(1000)
        assertTrue(aDiplomacy.getGoldGifts() > 0)
        assertTrue(bDiplomacy.getGoldGifts() > 0)
        bDiplomacy.declareWar()
        assertEquals(0, aDiplomacy.getGoldGifts())
        assertTrue(bDiplomacy.getGoldGifts() > 0)
    }

    @Test
    fun `Gifting gold reduces previous gifts taken` () {
        aDiplomacy.giftGold(1000, true)
        bDiplomacy.giftGold(500, true)
        assertTrue(aDiplomacy.getGoldGifts() > 0)
        assertTrue(aDiplomacy.getGoldGifts() < 1000)
        assertTrue(bDiplomacy.getGoldGifts() == 0)
    }

    @Test
    fun `Excess gold from a trade become a gift` () {
        a.addGold(1000)
        assertEquals(0, bDiplomacy.getGoldGifts())
        val tradeOffer = TradeLogic(a,b)
        tradeOffer.currentTrade.ourOffers.add(tradeOffer.ourAvailableOffers.first { it.type == TradeOfferType.Gold })
        assertTrue(TradeEvaluation().getTradeAcceptability(tradeOffer.currentTrade.reverse(), b,a,false) > 0)
        tradeOffer.acceptTrade()
        assertEquals(0, aDiplomacy.getGoldGifts())
        assertTrue(bDiplomacy.getGoldGifts() > 0)
    }

    @Test
    fun `Can ask for 90 percent of gold gift back again a turn later` () {
        // Due to rounding, we aren't going to assume we can get 100% of the gold back
        // Therefore we only test for 90%
        a.addGold(1000)
        val tradeOffer = TradeLogic(a,b)
        tradeOffer.currentTrade.ourOffers.add(tradeOffer.ourAvailableOffers.first { it.type == TradeOfferType.Gold })
        tradeOffer.acceptTrade()
        bDiplomacy.nextTurn()
        val tradeOffer2 = TradeLogic(a,b)
        tradeOffer2.currentTrade.theirOffers.add(TradeOffer("Gold", TradeOfferType.Gold, 900, testGame.gameInfo.speed))
        assertTrue(TradeEvaluation().getTradeAcceptability(tradeOffer.currentTrade.reverse(), b,a,false) > 0)
        tradeOffer2.acceptTrade()
        assertTrue(bDiplomacy.getGoldGifts() >= 0) // Must not be negative
    }

    @Test
    fun `Gold gifted impact trade acceptability`() {
        a.addGold(1000)
        val tradeOffer = TradeLogic(a,b)
        tradeOffer.currentTrade.ourOffers.add(tradeOffer.ourAvailableOffers.first { it.type == TradeOfferType.Gold })
        assertTrue(TradeEvaluation().getTradeAcceptability(tradeOffer.currentTrade, b,a,true) < 0)
        tradeOffer.acceptTrade()
        val tradeOffer2 = TradeLogic(a,b)
        assertTrue(TradeEvaluation().getTradeAcceptability(tradeOffer2.currentTrade.reverse(), b,a,true) > 0)
    }

    @Test
    fun `Gold gifted uses pure gold multiplier constant`() {
        a.addGold(1000)
        a.gameInfo.ruleset.modOptions.constants.goldGiftMultiplier = .5f
        a.gameInfo.ruleset.modOptions.constants.goldGiftTradeMultiplier = 0f
        val tradeOffer = TradeLogic(a,b)
        tradeOffer.currentTrade.ourOffers.add(tradeOffer.ourAvailableOffers.first { it.type == TradeOfferType.Gold })
        tradeOffer.acceptTrade()
        assertEquals(500, bDiplomacy.getGoldGifts())
    }

    @Test
    fun `Gold gifted uses gold multiplier constant`() {
        a.addGold(1000)
        b.addGold(500)
        a.gameInfo.ruleset.modOptions.constants.goldGiftMultiplier = 0f
        a.gameInfo.ruleset.modOptions.constants.goldGiftTradeMultiplier = .5f
        val tradeOffer = TradeLogic(a,b)
        tradeOffer.currentTrade.ourOffers.add(tradeOffer.ourAvailableOffers.first { it.type == TradeOfferType.Gold })
        tradeOffer.currentTrade.theirOffers.add(tradeOffer.theirAvailableOffers.first { it.type == TradeOfferType.Gold })
        tradeOffer.acceptTrade()
        assertEquals(250, bDiplomacy.getGoldGifts())
    }

    @Test
    fun `Gifted Gold Disapears using gold gift degradation multiplier constant` () {
        val c = testGame.addCiv()
        c.diplomacyFunctions.makeCivilizationsMeet(a)
        val caDiplomacy = c.getDiplomacyManager(a)!!
        a.gameInfo.ruleset.modOptions.constants.goldGiftDegradationMultiplier = 1f
        aDiplomacy.recieveGoldGifts(100)
        caDiplomacy.recieveGoldGifts(100)
        assertTrue(aDiplomacy.getGoldGifts() > 0)
        assertTrue(caDiplomacy.getGoldGifts() > 0)

        aDiplomacy.nextTurn()
        a.gameInfo.ruleset.modOptions.constants.goldGiftDegradationMultiplier = 10f
        caDiplomacy.nextTurn()

        assertTrue(caDiplomacy.getGoldGifts() < aDiplomacy.getGoldGifts())
    }

    @Test
    fun `Gifted Gold doesn't disapear when gold gift degradation multiplier constant is set to 0` () {
        a.gameInfo.ruleset.modOptions.constants.goldGiftDegradationMultiplier = 0f
        aDiplomacy.recieveGoldGifts(1000)
        assertTrue(aDiplomacy.getGoldGifts() > 0)
        val gold = aDiplomacy.getGoldGifts()
        aDiplomacy.nextTurn()
        val gold2 = aDiplomacy.getGoldGifts()
        assertTrue(gold == gold2)
    }

    @Test
    fun `Gifted Gold doesn't disapear when gold gift degradation multiplier constant is set to 0 with low gold` () {
        a.gameInfo.ruleset.modOptions.constants.goldGiftDegradationMultiplier = 0f
        aDiplomacy.recieveGoldGifts(10)
        assertTrue(aDiplomacy.getGoldGifts() > 0)
        val gold = aDiplomacy.getGoldGifts()
        aDiplomacy.nextTurn()
        val gold2 = aDiplomacy.getGoldGifts()
        assertTrue(gold == gold2)
    }

}

