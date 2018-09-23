package com.unciv.ui.trade

import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.trade.TradeOffersList
import com.unciv.logic.trade.TradeType
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.disable
import com.unciv.ui.utils.tr
import kotlin.math.min

class OffersList(val offers: TradeOffersList, val correspondingOffers: TradeOffersList,
                 val otherCivOffers: TradeOffersList, val otherCivCorrespondingOffers: TradeOffersList,
                 val onChange: () -> Unit) : ScrollPane(null) {
    val table= Table(CameraStageBaseScreen.skin).apply { defaults().pad(5f) }
    init {
        update()
    }


    fun update() {
        table.clear()
        for(offer in offers.sortedBy { it.type }) {
            var buttonText = offer.name.tr()
            if(offer.type !in listOf(TradeType.Technology, TradeType.City, TradeType.Introduction)) buttonText+=" ("+offer.amount+")"
            if(offer.duration>1) buttonText+="\n"+offer.duration+" {turns}".tr()
            val tb = TextButton(buttonText, CameraStageBaseScreen.skin)
            val amountPerClick =
                    if(offer.type== TradeType.Gold) 50
                    else 1
            if(offer.amount>0)
                tb.onClick {
                    val amountTransferred = min(amountPerClick, offer.amount)
                    offers += offer.copy(amount = -amountTransferred)
                    correspondingOffers += offer.copy(amount = amountTransferred)
                    if(offer.type== TradeType.Treaty) { // this goes both ways, so it doesn't matter which side you click
                        otherCivOffers += offer.copy(amount = -amountTransferred)
                        otherCivCorrespondingOffers += offer.copy(amount = amountTransferred)
                    }

                    onChange()
                    update()
                }
            else tb.disable()  // for instance we have negative gold
            table.add(tb).row()
        }
        widget = table
    }

}