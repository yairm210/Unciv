package com.unciv.ui.screens.diplomacyscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.trade.Trade
import com.unciv.logic.trade.TradeEvaluation
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeOfferType
import com.unciv.logic.trade.TradeOfferType.*
import com.unciv.logic.trade.TradeOffersList
import com.unciv.models.ruleset.tile.ResourceSupplyList
import com.unciv.models.translations.tr
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.setEnabled
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.ExpanderTab
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import kotlin.math.min
import com.unciv.ui.components.widgets.AutoScrollPane as ScrollPane

/**
 * Widget for one fourth of an [OfferColumnsTable] - instantiated for ours/theirs × available/traded
 * @param persistenceID  Part of ID added to [ExpanderTab.persistenceID] to distinguish the four usecases
 * @param onOfferClicked What to do when a tradeButton is clicked
 */
class OffersListScroll(
    private val persistenceID: String,
    private val onOfferClicked: (TradeOffer) -> Unit
) : ScrollPane(null) {
    val table = Table(BaseScreen.skin).apply { defaults().pad(5f) }


    private val expanderTabs = HashMap<TradeOfferType, ExpanderTab>()

    init {
        fadeScrollBars=false
        setScrollbarsVisible(true)
    }

    /**
     * @param offersToDisplay The offers which should be displayed as buttons
     * @param otherSideOffers The list of other side's offers to compare with whether these offers are unique
     * @param untradableOffers Things we got from sources that we can't trade on, displayed for completeness - should be aggregated per resource to "All" origin
     */
    fun update(
        offersToDisplay: TradeOffersList,
        otherSideOffers: TradeOffersList,
        untradableOffers: ResourceSupplyList = ResourceSupplyList.emptyList,
        ourCiv: Civilization,
        theirCiv: Civilization
    ) {
        table.clear()
        expanderTabs.clear()

        for (offerType in TradeOfferType.entries) {
            val labelName = when(offerType) {
                Gold, Gold_Per_Turn, Treaty, Agreement, Introduction -> ""
                Luxury_Resource -> "Luxury resources"
                Strategic_Resource -> "Strategic resources"
                Stockpiled_Resource -> "Stockpiled resources"
                Technology -> "Technologies"
                WarDeclaration -> "Declarations of war"
                PeaceProposal -> "Peace Proposals"
                City -> "Cities"
            }
            val offersOfType = offersToDisplay.filter { it.type == offerType }
            if (labelName.isNotEmpty() && offersOfType.any()) {
                expanderTabs[offerType] = ExpanderTab(labelName, persistenceID = "Trade.$persistenceID.$offerType") {
                    it.defaults().pad(5f)
                }
            }
        }

        for (offerType in TradeOfferType.entries) {
            val offersOfType = offersToDisplay.filter { it.type == offerType }
                .sortedWith(compareBy(
                    { if (UncivGame.Current.settings.orderTradeOffersByAmount) -it.amount else 0 },
                    { if (it.type==City) it.getOfferText() else it.name.tr() }
                ))

            if (expanderTabs.containsKey(offerType)) {
                expanderTabs[offerType]!!.innerTable.clear()
                table.add(expanderTabs[offerType]!!).row()
            }

            for (offer in offersOfType) {
                val tradeLabel = offer.getOfferText(untradableOffers.sumBy(offer.name))
                val tradeIcon = when (offer.type) {
                    Luxury_Resource, Strategic_Resource ->
                        ImageGetter.getResourcePortrait(offer.name, 30f)
                    WarDeclaration ->
                        ImageGetter.getNationPortrait(ourCiv.gameInfo.ruleset.nations[offer.name]!!, 30f)
                    PeaceProposal ->
                        ImageGetter.getNationPortrait(ourCiv.gameInfo.ruleset.nations[offer.name]!!, 30f)
                    else -> null
                }
                val tradeButton = IconTextButton(tradeLabel, tradeIcon).apply {
                    if (tradeIcon != null)
                        iconCell.size(30f)
                    label.setAlignment(Align.center)
                    labelCell.pad(5f).grow()
                }
                
                // Disable peace proposal trade item when:
                // 1. Third civ is stronger, need to trade with them instead
                // 2. Third civ is city state allied to civ we are war with
                // 3. War count down hasn't expired yet
                if (offer.type == PeaceProposal) {
                    val thirdCiv = ourCiv.gameInfo.getCivilization(offer.name)
                    
                    val trade = Trade()
                    val peaceOffer = TradeOffer(Constants.peaceTreaty, Treaty, offer.amount, offer.duration)
                    trade.ourOffers.add(peaceOffer)
                    trade.theirOffers.add(peaceOffer)

                    val tradeEval = TradeEvaluation()
                    var offerEnabled = true
                    var tooltipText = ""
                    var warCountDown = 0

                    if (persistenceID == "TheirAvail") {
                        val diploManager = theirCiv.getDiplomacyManager(thirdCiv)!!
                        warCountDown = if (diploManager.hasFlag(DiplomacyFlags.DeclaredWar))
                            diploManager.getFlag(DiplomacyFlags.DeclaredWar) else 0

                        if (warCountDown < 1) {
                            if (thirdCiv.isCityState) {
                                val allyCiv = thirdCiv.getAllyCiv()
                                if (allyCiv != null && theirCiv.isAtWarWith(allyCiv)) {
                                    // City state is allied to civ with whom they're at war with, need to trade peace with that civ instead
                                    offerEnabled = false
                                    tooltipText = thirdCiv.civName + " is allied to " + allyCiv.civName
                                }
                            }
                            else {
                                val thirdCivOK = tradeEval.isTradeAcceptable(trade, thirdCiv, theirCiv)
                                val theirCivOK = tradeEval.isTradeAcceptable(trade, theirCiv, thirdCiv)

                                if (!theirCivOK && thirdCivOK) tooltipText = theirCiv.civName + " doesn't want peace"
                                else if (theirCivOK && !thirdCivOK) tooltipText = thirdCiv.civName + " doesn't want peace"
                                else if (!theirCivOK) tooltipText = "None of the sides want peace"

                                // thirdCiv will agree to peace trade if it's weaker,
                                // otherwise we need to trade with them instead of this civ
                                offerEnabled = thirdCivOK
                            }
                        }
                    }
                    else if (persistenceID == "OurAvail") {
                        val diploManager = ourCiv.getDiplomacyManager(thirdCiv)!!
                        warCountDown = if (diploManager.hasFlag(DiplomacyFlags.DeclaredWar))
                            diploManager.getFlag(DiplomacyFlags.DeclaredWar) else 0

                        if (warCountDown < 1) {
                            if (thirdCiv.isCityState) {
                                val allyCiv = thirdCiv.getAllyCiv()
                                if (allyCiv != null && ourCiv.isAtWarWith(allyCiv)) {
                                    // City state is allied to civ we're at war with, need to trade peace with that civ instead
                                    offerEnabled = false
                                    tooltipText = thirdCiv.civName + " is allied to " + allyCiv.civName
                                }
                            }
                            else {
                                val thirdCivOK = tradeEval.isTradeAcceptable(trade, thirdCiv, ourCiv)
                                val ourCivOK = tradeEval.isTradeAcceptable(trade, ourCiv, thirdCiv)

                                if (!ourCivOK && thirdCivOK) tooltipText = ourCiv.civName + " doesn't want peace"
                                else if (ourCivOK && !thirdCivOK) tooltipText = thirdCiv.civName + " doesn't want peace"
                                else if (!ourCivOK) tooltipText = "None of the sides want peace"

                                // thirdCiv will agree to peace trade if it's weaker,
                                // otherwise we can't trade peace, trade partner needs to trade peace with tird civ
                                offerEnabled = thirdCivOK
                            }
                        }
                    }

                    if (warCountDown > 0) {
                        offerEnabled = false
                        tooltipText = "War countdown didn't expire"
                    }
                    
                    // TODO: Tooltip translation not handled
                    if (!offerEnabled) tradeButton.addTooltip(tooltipText)
                    // TODO: Tooltip won't be show if button is disabled
                    tradeButton.setEnabled(offerEnabled)
                }

                val amountPerClick =
                    when (offer.type) {
                        Gold -> 50
                        Treaty -> Int.MAX_VALUE
                        else -> 1
                    }

                if (offer.isTradable() && offer.name != Constants.peaceTreaty // can't disable peace treaty!
                    && (offer.name != Constants.researchAgreement // If we have a research agreement make sure the total gold of both Civs is higher than the total cost
                        // If both civs combined can pay for the research agreement, don't disable it. One can offer the other it's gold.
                        || (ourCiv.gold + theirCiv.gold > ourCiv.diplomacyFunctions.getResearchAgreementCost(theirCiv) * 2))) {

                    // highlight unique suggestions
                    if (offerType in listOf(Luxury_Resource, Strategic_Resource)
                            && otherSideOffers.all { it.type != offer.type || it.name != offer.name || it.amount < 0}) // we can 'have' negative amounts of resources 
                        tradeButton.color = Color.GREEN

                    tradeButton.onClick {
                        val amountTransferred = min(amountPerClick, offer.amount)
                        onOfferClicked(offer.copy(amount = amountTransferred))
                    }
                }
                else tradeButton.disable()  // for instance, we have negative gold


                if (expanderTabs.containsKey(offerType))
                    expanderTabs[offerType]!!.innerTable.add(tradeButton).row()
                else
                    table.add(tradeButton).row()
            }
        }
        actor = table
    }
}
