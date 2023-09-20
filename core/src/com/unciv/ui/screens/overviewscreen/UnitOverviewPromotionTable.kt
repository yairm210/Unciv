package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GUI
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.pickerscreens.PromotionPickerScreen

/** Supplies one "cell" actor to [UnitOverviewTabColumn.Promotions], encapsulated for readability */
class UnitOverviewPromotionTable(
    unit: MapUnit,
    actionContext: UnitOverviewTab
) : Table() {
    init {
        onClick {
            if (unit.promotions.canBePromoted() || unit.promotions.promotions.isNotEmpty()) {
                val game = actionContext.overviewScreen.game
                game.pushScreen(PromotionPickerScreen(unit) {
                    update(unit)
                })
            }
        }
    }

    private fun update(unit: MapUnit) {
        clearChildren()

        // getPromotions goes by json order on demand - so this is the same sorting as on UnitTable,
        // but not same as on PromotionPickerScreen (which e.g. tries to respect prerequisite proximity)
        val promotions = unit.promotions.getPromotions(true)
        val showPromoteStar = unit.promotions.canBePromoted()

        if (promotions.any()) {
            val iconCount = promotions.count() + (if (showPromoteStar) 1 else 0)
            val numberOfLines = (iconCount - 1) / 8 + 1  // Int math: -1,/,+1 means divide rounding *up*
            val promotionsPerLine = (iconCount - 1) / numberOfLines + 1
            for (linePromotions in promotions.chunked(promotionsPerLine)) {
                for (promotion in linePromotions) {
                    add(ImageGetter.getPromotionPortrait(promotion.name))
                }
                if (linePromotions.size == promotionsPerLine) row()
            }
        }

        if (!showPromoteStar) return
        add(
            ImageGetter.getImage("OtherIcons/Star").apply {
                color = if (GUI.isAllowedChangeState() && unit.currentMovement > 0f && unit.attacksThisTurn == 0)
                    Color.GOLDENROD
                else Color.GOLDENROD.darken(0.25f)
            }
        ).size(24f).padLeft(8f)
    }
}
