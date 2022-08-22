package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.map.MapUnit
import com.unciv.models.TutorialTrigger
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popup.AskTextPopup
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.RecreateOnResize
import com.unciv.ui.utils.extensions.isEnabled
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.surroundWithCircle
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton

class PromotionPickerScreen(val unit: MapUnit) : PickerScreen(), RecreateOnResize {
    private var selectedPromotion: Promotion? = null

    private fun acceptPromotion(promotion: Promotion?) {
        // if user managed to click disabled button, still do nothing
        if (promotion == null) return

        unit.promotions.addPromotion(promotion.name)
        if (unit.promotions.canBePromoted())
            game.replaceCurrentScreen(recreate())
        else
            game.popScreen()
    }

    init {
        setDefaultCloseAction()

        rightSideButton.setText("Pick promotion".tr())
        rightSideButton.onClick(UncivSound.Promote) {
            acceptPromotion(selectedPromotion)
        }

        val canBePromoted = unit.promotions.canBePromoted()
        val canChangeState = game.worldScreen!!.canChangeState
        val canPromoteNow = canBePromoted && canChangeState
                && unit.currentMovement > 0 && unit.attacksThisTurn == 0
        rightSideButton.isEnabled = canPromoteNow
        descriptionLabel.setText(updateDescriptionLabel())

        val availablePromotionsGroup = Table()
        availablePromotionsGroup.defaults().pad(5f)

        val unitType = unit.type
        val promotionsForUnitType = unit.civInfo.gameInfo.ruleSet.unitPromotions.values.filter {
            it.unitTypes.contains(unitType.name) || unit.promotions.promotions.contains(it.name)
        }
        val unitAvailablePromotions = unit.promotions.getAvailablePromotions()

        //Always allow the user to rename the unit as many times as they like.
        val renameButton = "Choose name for [${unit.name}]".toTextButton()
        renameButton.isEnabled = true

        renameButton.onClick {
            UnitRenamePopup(
                screen = this,
                unit = unit,
                actionOnClose = {
                    this.game.replaceCurrentScreen(PromotionPickerScreen(unit)) })
        }
        availablePromotionsGroup.add(renameButton)
        availablePromotionsGroup.row()

        for (promotion in promotionsForUnitType) {
            if (promotion.hasUnique(UniqueType.OneTimeUnitHeal) && unit.health == 100) continue
            val isPromotionAvailable = promotion in unitAvailablePromotions
            val unitHasPromotion = unit.promotions.promotions.contains(promotion.name)

            val selectPromotionButton = PickerPane.getPickerOptionButton(ImageGetter.getPromotionIcon(promotion.name), promotion.name)
            selectPromotionButton.isEnabled = true
            selectPromotionButton.onClick {
                val enable = canBePromoted && isPromotionAvailable && !unitHasPromotion && canChangeState
                selectedPromotion = if (enable) promotion else null
                rightSideButton.isEnabled = enable
                rightSideButton.setText(promotion.name.tr())

                descriptionLabel.setText(updateDescriptionLabel(promotion.getDescription(promotionsForUnitType)))
            }

            availablePromotionsGroup.add(selectPromotionButton)

            if (canPromoteNow && isPromotionAvailable) {
                val pickNow = "Pick now!".toLabel()
                pickNow.setAlignment(Align.center)
                pickNow.onClick {
                    acceptPromotion(promotion)
                }
                availablePromotionsGroup.add(pickNow).padLeft(10f).fillY()
            }
            else if (unitHasPromotion) selectPromotionButton.color = Color.GREEN
            else selectPromotionButton.color= Color.GRAY

            availablePromotionsGroup.row()

        }
        topTable.add(availablePromotionsGroup)

        displayTutorial(TutorialTrigger.Experience)
    }

    private fun setScrollY(scrollY: Float) {
        splitPane.pack()    // otherwise scrollPane.maxY == 0
        scrollPane.scrollY = scrollY
        scrollPane.updateVisualScroll()
    }

    private fun updateDescriptionLabel(): String {
        var newDescriptionText = unit.displayName().tr()

        return newDescriptionText.toString()
    }

    private fun updateDescriptionLabel(promotionDescription: String): String {
        var newDescriptionText = unit.displayName().tr()

        newDescriptionText += "\n" + promotionDescription

        return newDescriptionText.toString()
    }

    override fun recreate(): BaseScreen {
        val newScreen = PromotionPickerScreen(unit)
        newScreen.setScrollY(scrollPane.scrollY)
        return newScreen
    }
}
