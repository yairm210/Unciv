package com.unciv.ui.trade

import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.trade.TradeOffersList
import com.unciv.logic.trade.TradeType
import com.unciv.logic.trade.TradeType.*
import com.unciv.ui.cityscreen.ExpanderTab
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.disable
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.tr
import kotlin.math.min

class OffersList(val offers: TradeOffersList, val correspondingOffers: TradeOffersList,
                 val otherCivOffers: TradeOffersList, val otherCivCorrespondingOffers: TradeOffersList,
                 val onChange: () -> Unit) : ScrollPane(null) {
    val table = Table(CameraStageBaseScreen.skin).apply { defaults().pad(5f) }
    val tradesToNotHaveNumbers = listOf(Technology, City,
            Introduction, Treaty, WarDeclaration)

    val expanderTabs = HashMap<TradeType, ExpanderTab>()

    init {
        for (offertype in values()) {
            val labelName = when(offertype){
                Gold, Gold_Per_Turn, Treaty,Introduction -> ""
                Luxury_Resource -> "Luxury resources"
                Strategic_Resource -> "Luxury resources"
                Technology -> "Technologies"
                WarDeclaration -> "Declarations of war"
                City -> "Cities"
            }
            val offersOfType = offers.filter { it.type == offertype }
            if (labelName!="" && offersOfType.any()) {
                expanderTabs[offertype] = ExpanderTab(labelName, CameraStageBaseScreen.skin)
                expanderTabs[offertype]!!.close()
                expanderTabs[offertype]!!.innerTable.defaults().pad(5f)
            }
        }
        update()
    }


    fun update() {
        table.clear()
        for (offertype in values()) {
            val offersOfType = offers.filter { it.type == offertype }

            if (expanderTabs.containsKey(offertype)) {
                expanderTabs[offertype]!!.innerTable.clear()
                table.add(expanderTabs[offertype]!!).row()
            }

            for (offer in offersOfType) {
                var buttonText = offer.name.tr()
                if (offer.type !in tradesToNotHaveNumbers) buttonText += " (" + offer.amount + ")"
                if (offer.duration > 1) buttonText += "\n" + offer.duration + " {turns}".tr()
                val tradeButton = TextButton(buttonText, CameraStageBaseScreen.skin)
                val amountPerClick =
                        if (offer.type == Gold) 50
                        else 1
                if (offer.amount > 0)
                    tradeButton.onClick {
                        val amountTransferred = min(amountPerClick, offer.amount)
                        offers += offer.copy(amount = -amountTransferred)
                        correspondingOffers += offer.copy(amount = amountTransferred)
                        if (offer.type == Treaty) { // this goes both ways, so it doesn't matter which side you click
                            otherCivOffers += offer.copy(amount = -amountTransferred)
                            otherCivCorrespondingOffers += offer.copy(amount = amountTransferred)
                        }

                        onChange()
                        update()
                    }
                else tradeButton.disable()  // for instance we have negative gold


                if (expanderTabs.containsKey(offertype))
                    expanderTabs[offertype]!!.innerTable.add(tradeButton).row()
                else table.add(tradeButton).row()
            }
        }
        widget = table
    }

}