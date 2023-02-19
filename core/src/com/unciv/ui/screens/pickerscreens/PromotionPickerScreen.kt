package com.unciv.ui.screens.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.TutorialTrigger
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.components.BaseScreen
import com.unciv.ui.components.BorderedTable
import com.unciv.ui.components.RecreateOnResize
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.onDoubleClick
import com.unciv.ui.components.extensions.setFontColor
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import java.lang.Integer.max
import kotlin.math.abs

class PromotionNode(val promotion: Promotion) {
    var maxDepth = 0

    /** How many level this promotion has */
    var levels = 1

    val successors: ArrayList<PromotionNode> = ArrayList()
    val predecessors: ArrayList<PromotionNode> = ArrayList()

    val baseName = getBasePromotionName()

    fun isRoot() : Boolean {
        return predecessors.isEmpty()
    }

    fun calculateDepth(excludeNodes: ArrayList<PromotionNode>, currentDepth: Int) {
        maxDepth = max(maxDepth, currentDepth)
        excludeNodes.add(this)
        successors.filter { !excludeNodes.contains(it) }.forEach { it.calculateDepth(excludeNodes,currentDepth+1) }
    }

    private fun getBasePromotionName(): String {
        val nameWithoutBrackets = promotion.name.replace("[", "").replace("]", "")
        val level = when {
            nameWithoutBrackets.endsWith(" I") -> 1
            nameWithoutBrackets.endsWith(" II") -> 2
            nameWithoutBrackets.endsWith(" III") -> 3
            else -> 0
        }
        return nameWithoutBrackets.dropLast(if (level == 0) 0 else level + 1)
    }

    class CustomComparator(
        val baseNode: PromotionNode
    ) : Comparator<PromotionNode> {
        override fun compare(a: PromotionNode, b: PromotionNode): Int {
            val baseName = baseNode.baseName
            val aName = a.baseName
            val bName = b.baseName
            return when (aName) {
                baseName -> -1
                bName -> 0
                else -> 1
            }
        }
    }

}

class PromotionButton(
    val node: PromotionNode,
    val isPickable: Boolean = true,
    val isPromoted: Boolean = false

) : BorderedTable(
    path="PromotionScreen/PromotionButton",
    defaultBgShape = BaseScreen.skinStrings.roundedEdgeRectangleMidShape,
    defaultBgBorder = BaseScreen.skinStrings.roundedEdgeRectangleMidBorderShape) {

    var isSelected = false
    val label = node.promotion.name.toLabel().apply {
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

class PromotionPickerScreen(val unit: MapUnit) : PickerScreen(), RecreateOnResize {

    companion object Colors {
        val Default:Color = Color.BLACK
        val Selected:Color = colorFromRGB(72, 147, 175)
        val Promoted:Color = colorFromRGB(255, 215, 0).darken(0.2f)
        val Pickable:Color = colorFromRGB(28, 80, 0)
        val Prerequisite:Color = colorFromRGB(14, 92, 86)
    }

    private val promotionsTable = Table()
    private val promotionToButton = LinkedHashMap<String, PromotionButton>()
    private var selectedPromotion: PromotionButton? = null
    private var lines = ArrayList<Image>()

    private fun acceptPromotion(node: PromotionNode?) {
        // if user managed to click disabled button, still do nothing
        if (node == null) return

        unit.promotions.addPromotion(node.promotion.name)
        game.replaceCurrentScreen(recreate())
    }

    init {
        setDefaultCloseAction()

        rightSideButton.setText("Pick promotion".tr())
        rightSideButton.onClick(UncivSound.Promote) {
            if (selectedPromotion?.isPickable == true)
                acceptPromotion(selectedPromotion?.node)
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
        val promotionsForUnitType = unit.civ.gameInfo.ruleset.unitPromotions.values.filter {
            it.unitTypes.contains(unitType.name) || unit.promotions.promotions.contains(it.name)
        }
        //Always allow the user to rename the unit as many times as they like.
        val renameButton = "Choose name for [${unit.name}]".toTextButton()
        renameButton.isEnabled = true

        renameButton.onClick {
            if (!canChangeState) return@onClick
            UnitRenamePopup(
                screen = this,
                unit = unit,
                actionOnClose = {
                    game.replaceCurrentScreen(PromotionPickerScreen(unit))
                }
            )
        }
        availablePromotionsGroup.add(renameButton)

        topTable.add(availablePromotionsGroup).row()
        fillTable(promotionsForUnitType)

        displayTutorial(TutorialTrigger.Experience)
    }

    private fun fillTable(promotions: Collection<Promotion>) {
        val map = LinkedHashMap<String, PromotionNode>()

        val availablePromotions = unit.promotions.getAvailablePromotions()

        val canBePromoted = unit.promotions.canBePromoted()
        val canChangeState = game.worldScreen!!.canChangeState

        // Create nodes
        // Pass 1 - create nodes for all promotions
        for (promotion in promotions)
            map[promotion.name] = PromotionNode(promotion)

        // Pass 2 - remove nodes which are unreachable (dependent only on absent promotions)
        for (promotion in promotions) {
            if (promotion.prerequisites.isNotEmpty()) {
                val isReachable = promotion.prerequisites.any { map.containsKey(it) }
                if (!isReachable)
                    map.remove(promotion.name)
            }
        }

        // Pass 3 - fill nodes successors/predecessors, based on promotions prerequisites
        for (node in map.values) {
            for (prerequisiteName in node.promotion.prerequisites) {
                val prerequisiteNode = map[prerequisiteName]
                if (prerequisiteNode != null) {
                    node.predecessors.add(prerequisiteNode)
                    prerequisiteNode.successors.add(node)
                    // Prerequisite has the same base name -> +1 more level
                    if (prerequisiteNode.baseName == node.baseName)
                        prerequisiteNode.levels += 1
                }
            }
        }

        // Traverse each root node tree and calculate max possible depths of each node
        for (node in map.values) {
            if (node.isRoot())
                node.calculateDepth(arrayListOf(node), 0)
        }

        // For each non-root node remove all predecessors except the one with the least max depth.
        // This is needed to compactify trees and remove circular dependencies (A -> B -> C -> A)
        for (node in map.values) {
            if (node.isRoot())
                continue

            // Choose best predecessor - the one with less depth
            var best: PromotionNode? = null
            for (predecessor in node.predecessors) {
                if (best == null)
                    best = predecessor
                else
                    best = if (predecessor.maxDepth < best.maxDepth) predecessor else best
            }

            // Remove everything else, leave only best
            for (predecessor in node.predecessors)
                predecessor.successors.remove(node)
            node.predecessors.clear()
            node.predecessors.add(best!!)
            best.successors.add(node)
        }

        // Sort nodes successors so promotions with same base name go first
        for (node in map.values) {
            node.successors.sortWith(PromotionNode.CustomComparator(node))
        }

        // Create cell matrix
        val maxColumns = map.size + 1
        val maxRows = map.size + 1

        val cellMatrix = ArrayList<ArrayList<Cell<Actor>>>()
        for (y in 0..maxRows) {
            cellMatrix.add(ArrayList())
            for (x in 0..maxColumns) {
                val cell = promotionsTable.add()
                cellMatrix[y].add(cell)
            }
            promotionsTable.row()
        }

        /** Check whether cell is inhabited by actor already */
        fun isTherePlace(row: Int, col: Int, levels: Int) : Boolean {
            for (i in 0 until levels) {
                if (cellMatrix[row][col+i].actor != null)
                    return false
            }
            return true
        }

        /** Recursively place buttons for node and it's successors into free cells */
        fun placeButton(col: Int, row: Int, node: PromotionNode) : Int {
            val name = node.promotion.name
            // If promotion button not yet placed
            if (promotionToButton[name] == null) {
                // If place is free - we place button
                if (isTherePlace(row, col, node.levels)) {
                    val cell = cellMatrix[row][col]
                    val isPromotionAvailable = node.promotion in availablePromotions
                    val hasPromotion = unit.promotions.promotions.contains(name)
                    val isPickable = canBePromoted && isPromotionAvailable && !hasPromotion && canChangeState
                    val button = getButton(promotions, node, isPickable, hasPromotion)
                    promotionToButton[name] = button
                    cell.setActor(button)
                    cell.pad(5f)
                    cell.padRight(20f)
                    cell.minWidth(190f)
                    cell.maxWidth(190f)
                }
                // If place is not free - try to find another in the next row
                else {
                    return placeButton(col, row+1, node)
                }
            }

            // Filter successors who haven't been placed yet (to avoid circular dependencies)
            // and try to place them in the next column.
            // Return the max row this whole tree ever reached.
            return node.successors.filter {
                !promotionToButton.containsKey(it.promotion.name)
            }.map {
                placeButton(col+1, row, it)
            }.maxOfOrNull { it }?: row
        }

        // Build each tree starting from root nodes
        var row = 0
        for (node in map.values) {
            if (node.isRoot()) {
                row = placeButton(0, row, node)
                // Each root tree should start from a completely empty row.
                row += 1
            }
        }

        topTable.add(promotionsTable)

        addConnectingLines()

    }

    private fun getButton(allPromotions: Collection<Promotion>, node: PromotionNode,
                          isPickable: Boolean = true, isPromoted: Boolean = false) : PromotionButton {

        val button = PromotionButton(
            node = node,
            isPromoted = isPromoted,
            isPickable = isPickable
        )

        button.onClick {
            selectedPromotion?.isSelected = false
            selectedPromotion?.updateColor()
            selectedPromotion = button
            button.isSelected = true
            button.updateColor()

            for (btn in promotionToButton.values)
                btn.updateColor()
            button.node.promotion.prerequisites.forEach { promotionToButton[it]?.apply {
                if (!this.isPromoted)
                    bgColor = Prerequisite }}

            rightSideButton.isEnabled = isPickable
            rightSideButton.setText(node.promotion.name.tr())
            descriptionLabel.setText(updateDescriptionLabel(node.promotion.getDescription(allPromotions)))

            addConnectingLines()
        }

        if (isPickable)
            button.onDoubleClick(UncivSound.Promote) {
                acceptPromotion(node)
            }

        return button
    }

    private fun addConnectingLines() {
        promotionsTable.pack()
        scrollPane.updateVisualScroll()

        for (line in lines) line.remove()
        lines.clear()

        for (button in promotionToButton.values) {
            for (prerequisite in button.node.promotion.prerequisites) {
                val prerequisiteButton = promotionToButton[prerequisite] ?: continue

                var buttonCoords = Vector2(0f, button.height / 2)
                button.localToStageCoordinates(buttonCoords)
                promotionsTable.stageToLocalCoordinates(buttonCoords)

                var prerequisiteCoords = Vector2(prerequisiteButton.width, prerequisiteButton.height / 2)
                prerequisiteButton.localToStageCoordinates(prerequisiteCoords)
                promotionsTable.stageToLocalCoordinates(prerequisiteCoords)

                val lineColor = when {
                    button.isSelected -> Selected
                    prerequisiteButton.node.baseName == button.node.baseName -> Color.WHITE.cpy()
                    else -> Color.CLEAR
                }
                val lineSize = when {
                    button.isSelected -> 4f
                    else -> 2f
                }

                if (buttonCoords.x < prerequisiteCoords.x) {
                    val temp = buttonCoords.cpy()
                    buttonCoords = prerequisiteCoords
                    prerequisiteCoords = temp
                }


                if (buttonCoords.y != prerequisiteCoords.y) {

                    val deltaX = buttonCoords.x - prerequisiteCoords.x
                    val deltaY = buttonCoords.y - prerequisiteCoords.y
                    val halfLength = deltaX / 2f

                    val line = ImageGetter.getWhiteDot().apply {
                        width = halfLength+lineSize/2
                        height = lineSize
                        x = prerequisiteCoords.x
                        y = prerequisiteCoords.y - lineSize / 2
                    }
                    val line1 = ImageGetter.getWhiteDot().apply {
                        width = halfLength + lineSize/2
                        height = lineSize
                        x = buttonCoords.x - width
                        y = buttonCoords.y - lineSize / 2
                    }
                    val line2 = ImageGetter.getWhiteDot().apply {
                        width = lineSize
                        height = abs(deltaY)
                        x = buttonCoords.x - halfLength - lineSize / 2
                        y = buttonCoords.y + (if (deltaY > 0f) -height-lineSize/2 else lineSize/2)
                    }

                    line.color = lineColor
                    line1.color = lineColor
                    line2.color = lineColor

                    promotionsTable.addActor(line)
                    promotionsTable.addActor(line1)
                    promotionsTable.addActor(line2)

                    line.toBack()
                    line1.toBack()
                    line2.toBack()

                    lines.add(line)
                    lines.add(line1)
                    lines.add(line2)

                } else {

                    val line = ImageGetter.getWhiteDot().apply {
                        width = buttonCoords.x - prerequisiteCoords.x
                        height = lineSize
                        x = prerequisiteCoords.x
                        y = prerequisiteCoords.y - lineSize / 2
                    }
                    line.color = lineColor
                    promotionsTable.addActor(line)
                    line.toBack()
                    lines.add(line)

                }
            }
        }

        for (line in lines) {
            if (line.color == Selected)
                line.zIndex = lines.size
        }
    }

    private fun setScrollY(scrollY: Float) {
        splitPane.pack()    // otherwise scrollPane.maxY == 0
        scrollPane.scrollY = scrollY
        scrollPane.updateVisualScroll()
    }

    private fun updateDescriptionLabel(): String {
        return unit.displayName().tr()
    }

    private fun updateDescriptionLabel(promotionDescription: String): String {
        var newDescriptionText = unit.displayName().tr()
        newDescriptionText += "\n" + promotionDescription
        return newDescriptionText
    }

    override fun recreate(): BaseScreen {
        val newScreen = PromotionPickerScreen(unit)
        newScreen.setScrollY(scrollPane.scrollY)
        return newScreen
    }
}
