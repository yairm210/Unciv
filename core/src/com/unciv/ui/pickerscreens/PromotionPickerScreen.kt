package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.unciv.logic.map.MapUnit
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.unit.UnitPromotion
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.addClickListener
import com.unciv.ui.utils.setFontColor

class PromotionPickerScreen(mapUnit: MapUnit) : PickerScreen() {
    private var selectedPromotion: UnitPromotion? = null

    init {
        rightSideButton.setText("Pick promotion")
        rightSideButton.addClickListener {
            // todo add promotion to unit and decrease XP
            game.setWorldScreen()
            dispose()
        }

        val availablePromotions = VerticalGroup()
        availablePromotions.space(10f)
        for (promotion in GameBasics.UnitPromotions.values) {
            if (!promotion.unitTypes.contains(mapUnit.getBaseUnit().unitType)) continue
            val promotionButton = Button(skin)

            promotionButton.add(ImageGetter.getPromotionIcon(promotion.name)).size(30f).pad(10f)

            promotionButton.add(Label(promotion.name, skin)
                    .setFontColor(Color.WHITE)).pad(10f)

            promotionButton.addClickListener {
                selectedPromotion = promotion
                pick(promotion.name)
                descriptionLabel.setText(promotion.effect)
            }
            availablePromotions.addActor(promotionButton)
        }
        topTable.add(availablePromotions)
    }
}