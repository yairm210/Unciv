package com.unciv.ui.screens.pickerscreens

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.ui.components.widgets.BorderedTable
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

internal class PromotionButton(
    val node: PromotionTree.PromotionNode,
    val isPickable: Boolean,
    private val adoptedLabelStyle: Label.LabelStyle,
    maxWidth: Float
) : BorderedTable(
    path="PromotionScreen/PromotionButton",
    defaultBgShape = BaseScreen.skinStrings.roundedEdgeRectangleMidShape,
    defaultBgBorder = BaseScreen.skinStrings.roundedEdgeRectangleMidBorderShape
) {
    private val label = node.promotion.name.toLabel(hideIcons = true)
    private val defaultLabelStyle = label.style
    private val colors = BaseScreen.skin[PromotionScreenColors::class.java]

    init {

        touchable = Touchable.enabled
        borderSize = 5f

        pad(5f)
        align(Align.left)
        add(ImageGetter.getPromotionPortrait(node.promotion.name)).padRight(10f)
        label.setEllipsis(true)
        add(label).left().maxWidth(maxWidth)

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
