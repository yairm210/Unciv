package com.unciv.ui.trade

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeOffersList
import com.unciv.logic.trade.TradeType
import com.unciv.logic.trade.TradeType.*
import com.unciv.models.ruleset.tile.ResourceSupply
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import kotlin.math.min
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

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

    /**
     * @param offersToDisplay The offers which should be displayed as buttons
     * @param otherOffers The list of other side's offers to compare with whether these offers are unique
     * @param untradableOffers Things we got from sources that we can't trade on, displayed for completeness
     */
    fun update(offersToDisplay:TradeOffersList, otherOffers: TradeOffersList, untradableOffers: List<ResourceSupply> = emptyList()) {
        table.clear()
        expanderTabs.clear()

        for (offerType in values()) {
            val labelName = when(offerType){
                Gold, Gold_Per_Turn, Treaty, Agreement, Introduction -> ""
                Luxury_Resource -> "Luxury resources"
                Strategic_Resource -> "Strategic resources"
                Technology -> "Technologies"
                WarDeclaration -> "Declarations of war"
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
                val tradeButton = offer.getOfferText(untradableOffers.filter { it.resource.name == offer.name }.sumOf { it.amount }).toTextButton()
                tradeButton.labelCell.pad(5f)
                when (offer.type) {
                    Luxury_Resource, Strategic_Resource ->
                        tradeButton.add(ImageGetter.getResourceImage(offer.name, 30f))
                    WarDeclaration ->
                        tradeButton.add(ImageGetter.getNationIndicator(UncivGame.Current.gameInfo.ruleSet.nations[offer.name]!!, 30f))
                    else -> Unit // Remove warning
                }

                val amountPerClick =
                        if (offer.type == Gold) 50
                        else 1
                if (offer.amount > 0 && offer.name != Constants.peaceTreaty && // can't disable peace treaty!
                        offer.name != Constants.researchAgreement) {

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
