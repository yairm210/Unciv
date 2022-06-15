package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.map.MapUnit
import com.unciv.models.Tutorial
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popup.AskTextPopup
import com.unciv.ui.utils.extensions.isEnabled
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.surroundWithCircle
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton

class PromotionPickerScreen(val unit: MapUnit) : PickerScreen() {
    private var selectedPromotion: Promotion? = null

    private fun acceptPromotion(promotion: Promotion?) {
        // if user managed to click disabled button, still do nothing
        if (promotion == null) return

        unit.promotions.addPromotion(promotion.name)
        if (unit.promotions.canBePromoted())
            game.setScreen(PromotionPickerScreen(unit).setScrollY(scrollPane.scrollY))
        else
            game.resetToWorldScreen()
    }

    init {
        onBackButtonClicked { UncivGame.Current.resetToWorldScreen() }
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

        val availablePromotionsGroup = Table()
        availablePromotionsGroup.defaults().pad(5f)

        val unitType = unit.type
        val promotionsForUnitType = unit.civInfo.gameInfo.ruleSet.unitPromotions.values.filter {
            it.unitTypes.contains(unitType.name) || unit.promotions.promotions.contains(it.name)
        }
        val unitAvailablePromotions = unit.promotions.getAvailablePromotions()

        if (canPromoteNow && unit.instanceName == null) {
            val renameButton = "Choose name for [${unit.name}]".toTextButton()
            renameButton.isEnabled = true
            renameButton.onClick {
                AskTextPopup(
                    this,
                    label = "Choose name for [${unit.baseUnit.name}]",
                    icon = ImageGetter.getUnitIcon(unit.name).surroundWithCircle(80f),
                    defaultText = unit.name,
                    validate = { it != unit.name},
                    actionOnOk = { userInput ->
                        unit.instanceName = userInput
                        this.game.setScreen(PromotionPickerScreen(unit))
                    }
                ).open()
            }
            availablePromotionsGroup.add(renameButton)
            availablePromotionsGroup.row()
        }
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

                descriptionLabel.setText(promotion.getDescription(promotionsForUnitType))
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

        displayTutorial(Tutorial.Experience)
    }

    private fun setScrollY(scrollY: Float): PromotionPickerScreen {
        splitPane.pack()    // otherwise scrollPane.maxY == 0
        scrollPane.scrollY = scrollY
        scrollPane.updateVisualScroll()
        return this
    }

    override fun resize(width: Int, height: Int) {
        if (stage.viewport.screenWidth != width || stage.viewport.screenHeight != height) {
            game.setScreen(PromotionPickerScreen(unit).setScrollY(scrollPane.scrollY))
        }
    }
}
