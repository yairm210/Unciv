package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.unciv.logic.map.MapUnit
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.unit.Promotion
import com.unciv.ui.utils.*

class PromotionPickerScreen(mapUnit: MapUnit) : PickerScreen() {
    private var selectedPromotion: Promotion? = null


    init {
        rightSideButton.setText("Pick promotion")
        rightSideButton.addClickListener {
            mapUnit.promotions.addPromotion(selectedPromotion!!.name)
            game.setWorldScreen()
            dispose()
        }

        val availablePromotions = VerticalGroup()
        availablePromotions.space(10f)
        val unitType = mapUnit.getBaseUnit().unitType
        for (promotion in GameBasics.UnitPromotions.values) {
            if (!promotion.unitTypes.contains(unitType.toString())) continue
            val isPromotionAvailable = promotion.prerequisites.all { mapUnit.promotions.promotions.contains(it) }
            val unitHasPromotion = mapUnit.promotions.promotions.contains(promotion.name)
            val promotionButton = Button(skin)

            if(!isPromotionAvailable) promotionButton.color = Color.GRAY
            promotionButton.add(ImageGetter.getPromotionIcon(promotion.name)).size(30f).pad(10f)
            promotionButton.add(Label(promotion.name, skin)
                    .setFontColor(Color.WHITE)).pad(10f)
            if(unitHasPromotion) promotionButton.color = Color.GREEN

            promotionButton.addClickListener {
                selectedPromotion = promotion
                rightSideButton.setText(promotion.name)
                if(isPromotionAvailable && !unitHasPromotion) rightSideButton.enable()
                else rightSideButton.disable()
                var descriptionText = promotion.effect
                if(promotion.prerequisites.isNotEmpty()) descriptionText +="\nRequires: "+promotion.prerequisites.joinToString()
                descriptionLabel.setText(descriptionText)
            }
            availablePromotions.addActor(promotionButton)
        }
        topTable.add(availablePromotions)
    }
}