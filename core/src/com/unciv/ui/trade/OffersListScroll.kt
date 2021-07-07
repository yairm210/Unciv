package com.unciv.ui.trade

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeOffersList
import com.unciv.logic.trade.TradeType
import com.unciv.logic.trade.TradeType.*
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import kotlin.math.min
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class OffersListScroll(private val persistPrefix: String, val onOfferClicked: (TradeOffer) -> Unit) : ScrollPane(null) {
    val table = Table(CameraStageBaseScreen.skin).apply { defaults().pad(5f) }


    private val expanderTabs = HashMap<TradeType, ExpanderTab>()

    /**
     *   offersToDisplay - the offers which should be displayed as buttons
     *   otherOffers - the list of other side's offers to compare with whether these offers are unique
     */
    fun update(offersToDisplay:TradeOffersList, otherOffers: TradeOffersList) {
        table.clear()
        expanderTabs.forEach { it.value.saveState() }
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
                expanderTabs[offerType] = ExpanderTab(labelName, persistPrefix = persistPrefix) {
                    it.defaults().pad(5f)
                }
            }
        }

        for (offerType in values()) {
            val offersOfType = offersToDisplay.filter { it.type == offerType }
                    .sortedWith(compareBy({
                        if (UncivGame.Current.settings.orderTradeOffersByAmount) -it.amount else 0},
                            {if (it.type==City) it.getOfferText() else it.name.tr()}))

            if (expanderTabs.containsKey(offerType)) {
                expanderTabs[offerType]!!.innerTable.clear()
                table.add(expanderTabs[offerType]!!).row()
            }

            for (offer in offersOfType) {
                val tradeButton = offer.getOfferText().toTextButton()
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
                else table.add(tradeButton).row()
            }
        }
        actor = table
    }
}
