package com.unciv.ui.screens.diplomacyscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeOffersList
import com.unciv.logic.trade.TradeType
import com.unciv.logic.trade.TradeType.Agreement
import com.unciv.logic.trade.TradeType.City
import com.unciv.logic.trade.TradeType.Gold
import com.unciv.logic.trade.TradeType.Gold_Per_Turn
import com.unciv.logic.trade.TradeType.Introduction
import com.unciv.logic.trade.TradeType.Luxury_Resource
import com.unciv.logic.trade.TradeType.Strategic_Resource
import com.unciv.logic.trade.TradeType.Technology
import com.unciv.logic.trade.TradeType.Treaty
import com.unciv.logic.trade.TradeType.WarDeclaration
import com.unciv.logic.trade.TradeType.PeaceDeclaration
import com.unciv.logic.trade.TradeType.values
import com.unciv.models.ruleset.tile.ResourceSupplyList
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.disable
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


    private val expanderTabs = HashMap<TradeType, ExpanderTab>()

    init {
        fadeScrollBars=false
        setScrollbarsVisible(true)
    }

    /**
     * @param offersToDisplay The offers which should be displayed as buttons
     * @param otherOffers The list of other side's offers to compare with whether these offers are unique
     * @param untradableOffers Things we got from sources that we can't trade on, displayed for completeness - should be aggregated per resource to "All" origin
     */
    fun update(
        offersToDisplay: TradeOffersList,
        otherOffers: TradeOffersList,
        untradableOffers: ResourceSupplyList = ResourceSupplyList.emptyList,
        ourCiv: Civilization,
        theirCiv: Civilization
    ) {
        table.clear()
        expanderTabs.clear()

        for (offerType in values()) {
            val labelName = when(offerType) {
                Gold, Gold_Per_Turn, Treaty, Agreement, Introduction -> ""
                Luxury_Resource -> "Luxury resources"
                Strategic_Resource -> "Strategic resources"
                Technology -> "Technologies"
                WarDeclaration -> "Declarations of war"
                PeaceDeclaration -> "Declaration of peace"
                City -> "Cities"
            }
            val offersOfType = offersToDisplay.filter { it.type == offerType }
            if (labelName.isNotEmpty() && offersOfType.any()) {
                expanderTabs[offerType] = ExpanderTab(labelName, persistenceID = "Trade.$persistenceID.$offerType") {
                    it.defaults().pad(5f)
                }
            }
        }

        for (offerType in values()) {
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
                        ImageGetter.getNationPortrait(UncivGame.Current.gameInfo!!.ruleset.nations[offer.name]!!, 30f)
                    else -> null
                }
                val tradeButton = IconTextButton(tradeLabel, tradeIcon).apply {
                    if (tradeIcon != null)
                        iconCell.size(30f)
                    label.setAlignment(Align.center)
                    labelCell.pad(5f).grow()
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
                            && otherOffers.all { it.type != offer.type || it.name != offer.name })
                        tradeButton.color = Color.GREEN

                    tradeButton.onClick {
                        val amountTransferred = min(amountPerClick, offer.amount)
                        onOfferClicked(offer.copy(amount = amountTransferred))
                    }
                }
                else tradeButton.disable()  // for instance we have negative gold


                if (expanderTabs.containsKey(offerType))
                    expanderTabs[offerType]!!.innerTable.add(tradeButton).row()
                else
                    table.add(tradeButton).row()
            }
        }
        actor = table
    }
}
