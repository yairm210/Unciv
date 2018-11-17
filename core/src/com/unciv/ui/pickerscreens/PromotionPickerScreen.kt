package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.unciv.UnCivGame
import com.unciv.logic.map.MapUnit
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.unit.Promotion
import com.unciv.ui.utils.*

class PromotionPickerScreen(mapUnit: MapUnit) : PickerScreen() {
    private var selectedPromotion: Promotion? = null


    init {
        onBackButtonClicked { UnCivGame.Current.setWorldScreen(); dispose() }
        rightSideButton.setText("Pick promotion")
        rightSideButton.onClick {
            mapUnit.promotions.addPromotion(selectedPromotion!!.name)
            if(mapUnit.promotions.canBePromoted()) game.screen = PromotionPickerScreen(mapUnit)
            else game.setWorldScreen()
            dispose()
        }

        val availablePromotionsGroup = VerticalGroup()
        availablePromotionsGroup.space(10f)
        val unitType = mapUnit.type
        val promotionsForUnitType = GameBasics.UnitPromotions.values.filter { it.unitTypes.contains(unitType.toString()) }
        val unitAvailablePromotions = mapUnit.promotions.getAvailablePromotions()
        for (promotion in promotionsForUnitType) {
            val isPromotionAvailable = promotion in unitAvailablePromotions
            val unitHasPromotion = mapUnit.promotions.promotions.contains(promotion.name)
            val promotionButton = Button(skin)

            if(!isPromotionAvailable) promotionButton.color = Color.GRAY
            promotionButton.add(ImageGetter.getPromotionIcon(promotion.name)).size(30f).pad(10f)
            promotionButton.add(Label(promotion.name, skin)
                    .setFontColor(Color.WHITE)).pad(10f)
            if(unitHasPromotion) promotionButton.color = Color.GREEN

            promotionButton.onClick {
                selectedPromotion = promotion
                rightSideButton.setText(promotion.name)
                if(isPromotionAvailable && !unitHasPromotion) rightSideButton.enable()
                else rightSideButton.disable()
                var descriptionText = promotion.effect
                if(promotion.prerequisites.isNotEmpty()) descriptionText +="\nRequires: "+
                        promotion.prerequisites.filter { promotionsForUnitType.any { promotion ->  promotion.name==it } }
                                .joinToString(" OR ")
                descriptionLabel.setText(descriptionText)
            }
            availablePromotionsGroup.addActor(promotionButton)
        }
        topTable.add(availablePromotionsGroup)
    }
}