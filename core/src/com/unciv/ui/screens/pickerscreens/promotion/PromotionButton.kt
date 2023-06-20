package com.unciv.ui.screens.pickerscreens.promotion

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.Align
import com.unciv.ui.components.BorderedTable
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.setFontColor
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

internal class PromotionButton(
    val node: PromotionNodeOld,
    val isPickable: Boolean = true,
    val isPromoted: Boolean = false
) : BorderedTable(
    path="PromotionScreen/PromotionButton",
    defaultBgShape = BaseScreen.skinStrings.roundedEdgeRectangleMidShape,
    defaultBgBorder = BaseScreen.skinStrings.roundedEdgeRectangleMidBorderShape
) {

    var isSelected = false
    val label = node.promotion.name.toLabel(hideIcons = true).apply {
        wrap = false
        setAlignment(Align.left)
        setEllipsis(true)
    }

    init {

        touchable = Touchable.enabled
        borderSize = 5f

        pad(5f)
        align(Align.left)
        add(ImageGetter.getPromotionPortrait(node.promotion.name)).padRight(10f)
        add(label).left().maxWidth(130f)

        updateColor()
    }

    fun updateColor() {

        val color = when {
            isSelected -> PromotionPickerScreen.Selected
            isPickable -> PromotionPickerScreen.Pickable
            isPromoted -> PromotionPickerScreen.Promoted
            else -> PromotionPickerScreen.Default
        }

        bgColor = color

        val textColor = when {
            isSelected -> Color.WHITE
            isPromoted -> PromotionPickerScreen.Promoted.cpy().darken(0.8f)
            else -> Color.WHITE
        }
        label.setFontColor(textColor)
    }

}
