package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.map.MapUnit
import com.unciv.models.Tutorial
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*

class PromotionPickerScreen(val unit: MapUnit) : PickerScreen() {
    private var selectedPromotion: Promotion? = null


    fun acceptPromotion(promotion: Promotion?) {
        unit.promotions.addPromotion(promotion!!.name)
        if(unit.promotions.canBePromoted()) game.setScreen(PromotionPickerScreen(unit))
        else game.setWorldScreen()
        dispose()
        game.worldScreen.shouldUpdate=true
    }

    init {
        onBackButtonClicked { UncivGame.Current.setWorldScreen() }
        setDefaultCloseAction()


        rightSideButton.setText("Pick promotion".tr())
        rightSideButton.onClick(UncivSound.Promote) {
          acceptPromotion(selectedPromotion)
        }
        val canBePromoted = unit.promotions.canBePromoted()
        val canChangeState = game.worldScreen.canChangeState
        if(!canBePromoted || !canChangeState)
            rightSideButton.disable()

        val availablePromotionsGroup = Table()
        availablePromotionsGroup.defaults().pad(5f)

        val unitType = unit.type
        val promotionsForUnitType = unit.civInfo.gameInfo.ruleSet.unitPromotions.values.filter {
            it.unitTypes.contains(unitType.toString())
                    || unit.promotions.promotions.contains(it.name) }
        val unitAvailablePromotions = unit.promotions.getAvailablePromotions()

        for (promotion in promotionsForUnitType) {
            if(promotion.name=="Heal Instantly" && unit.health==100) continue
            val isPromotionAvailable = promotion in unitAvailablePromotions
            val unitHasPromotion = unit.promotions.promotions.contains(promotion.name)

            val selectPromotionButton = Button(skin)
            selectPromotionButton.add(ImageGetter.getPromotionIcon(promotion.name)).size(30f).pad(10f)
            selectPromotionButton.add(promotion.name.toLabel()).pad(10f).padRight(20f)
            selectPromotionButton.touchable = Touchable.enabled
            selectPromotionButton.onClick {
                selectedPromotion = promotion
                rightSideButton.setText(promotion.name.tr())
                if(canBePromoted && isPromotionAvailable && !unitHasPromotion && canChangeState)
                    rightSideButton.enable()
                else rightSideButton.disable()


                descriptionLabel.setText(promotion.getDescription(promotionsForUnitType))
            }

            availablePromotionsGroup.add(selectPromotionButton)

            if(canBePromoted && isPromotionAvailable && canChangeState) {
                val pickNow = "Pick now!".toLabel()
                pickNow.setAlignment(Align.center)
                pickNow.onClick {
                    acceptPromotion(promotion)
                }
                availablePromotionsGroup.add(pickNow).padLeft(10f).fillY()
            }
            else if(unitHasPromotion) selectPromotionButton.color= Color.GREEN
            else selectPromotionButton.color= Color.GRAY

            availablePromotionsGroup.row()

        }
        topTable.add(availablePromotionsGroup)

        displayTutorial(Tutorial.Experience)
    }
}