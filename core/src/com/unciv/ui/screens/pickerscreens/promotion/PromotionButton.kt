package com.unciv.ui.screens.pickerscreens.promotion

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.ui.components.BorderedTable
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

internal class PromotionButton(
    val node: PromotionTree.PromotionNode,
    val isPickable: Boolean,
    private val adoptedLabelStyle: Label.LabelStyle
) : BorderedTable(
    path="PromotionScreen/PromotionButton",
    defaultBgShape = BaseScreen.skinStrings.roundedEdgeRectangleMidShape,
    defaultBgBorder = BaseScreen.skinStrings.roundedEdgeRectangleMidBorderShape
) {
    class PromotionButtonColors {
        val default: Color = Color.BLACK
        val selected: Color = colorFromRGB(72, 147, 175)
        val pathToSelection: Color = selected.darken(0.33f)
        val promoted: Color = colorFromRGB(255, 215, 0).darken(0.2f)
        val promotedText: Color = promoted.darken(0.8f)
        val pickable: Color = colorFromRGB(28, 80, 0)
        val prerequisite: Color = Color.ROYAL // colorFromRGB(14, 92, 86)
    }

    private val label = node.promotion.name.toLabel(hideIcons = true)
    private val defaultLabelStyle = label.style
    private val colors = BaseScreen.skin[PromotionButtonColors::class.java]

    init {

        touchable = Touchable.enabled
        borderSize = 5f

        pad(5f)
        align(Align.left)
        add(ImageGetter.getPromotionPortrait(node.promotion.name)).padRight(10f)
        label.setEllipsis(true)
        add(label).left().maxWidth(130f)

        updateColor(false, emptySet(), emptySet())
    }

    fun updateColor(isSelected: Boolean, pathToSelection: Set<Promotion>, prerequisites: Set<PromotionTree.PromotionNode>) {
        bgColor = when {
            isSelected -> colors.selected
            node.isAdopted -> colors.promoted
            node.promotion in pathToSelection -> colors.pathToSelection
            node in prerequisites -> colors.prerequisite
            isPickable -> colors.pickable
            else -> colors.default
        }

        label.style = if (!isSelected && node.isAdopted) adoptedLabelStyle
            else defaultLabelStyle
    }

}
