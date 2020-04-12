package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.map.MapUnit
import com.unciv.models.Tutorial
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*

class PromotionPickerScreen(val unit: MapUnit) : PickerScreen() {
    private data class PickedPromotionAndButton (val promotion: Promotion, val button: Button, val enable: Boolean)
    private var selectedPromotion: PickedPromotionAndButton? = null
    private var highlightedButton: Button? = null
    private val promotionsForUnitType: List<Promotion>

    private fun acceptPromotion(promotion: Promotion?) {
        if (promotion == null) return
        unit.promotions.addPromotion (promotion.name)
        if (unit.promotions.canBePromoted()) game.setScreen (PromotionPickerScreen(unit))
        else game.setWorldScreen()
        dispose()
        game.worldScreen.shouldUpdate = true
    }

    init {
        onBackButtonClicked { UncivGame.Current.setWorldScreen() }
        setDefaultCloseAction()

        setAcceptButtonAction ("Pick promotion", UncivSound.Promote) {
            acceptPromotion (selectedPromotion?.promotion)
        }

        val canBePromoted = unit.promotions.canBePromoted()
        if (!canBePromoted)
            rightSideButton.disable()

        val availablePromotionsGroup = Table()
        availablePromotionsGroup.defaults().pad(5f)

        val unitType = unit.type
        promotionsForUnitType = unit.civInfo.gameInfo.ruleSet.unitPromotions.values.filter {
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
            val action = {
                pickPromotion(PickedPromotionAndButton(promotion, selectPromotionButton, canBePromoted && isPromotionAvailable && !unitHasPromotion))
            }
            selectPromotionButton.onClick (action)
            registerKeyHandler (promotion.name.tr(), action)

            availablePromotionsGroup.add(selectPromotionButton)

            if(canBePromoted && isPromotionAvailable) {
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

    private fun pickPromotion (promotionAndButton: PickedPromotionAndButton) {
        selectedPromotion = promotionAndButton
        rightSideButton.setText(promotionAndButton.promotion.name.tr())
        if (promotionAndButton.enable)
            rightSideButton.enable()
        else rightSideButton.disable()

        descriptionLabel.setText(promotionAndButton.promotion.getDescription(promotionsForUnitType))

        promotionAndButton.button.highlight()
    }

    private fun Button.highlight (highlight: Boolean = true) {
        if (highlight) {
            highlightedButton?.highlight(false)
            highlightedButton = null
        }
        val newColor = if (highlight) Color.GOLDENROD else Color.WHITE
        //(this.children.firstOrNull { it is Image } as Image?)?.color = newColor
        (this.children.firstOrNull { it is Label } as Label?)?.color = newColor
        if (highlight) {
            highlightedButton = this
            scrollPane.scrollTo(x, y, width, height)
        }
    }
}