package com.unciv.ui.screens.pickerscreens.promotion

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GUI
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.TutorialTrigger
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.translations.tr
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.onDoubleClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.RecreateOnResize
import com.unciv.ui.screens.pickerscreens.PickerScreen
import kotlin.math.abs


class PromotionPickerScreen(val unit: MapUnit) : PickerScreen(), RecreateOnResize {
    private val buttonColors = skin[PromotionButton.PromotionButtonColors::class.java]
    private val promotedLabelStyle = Label.LabelStyle(skin[Label.LabelStyle::class.java]).apply {
        fontColor = buttonColors.promotedText
    }

    private val promotionsTable = Table()
    private val promotionToButton = LinkedHashMap<String, PromotionButton>()
    private var selectedPromotion: PromotionButton? = null
    private var lines = ArrayList<Image>()

    // [acceptPromotion] will [recreate] the screen, so these are constant for this picker's lifetime
    private val canChangeState = GUI.isAllowedChangeState()
    private val canBePromoted = unit.promotions.canBePromoted()
    private val canPromoteNow = canChangeState && canBePromoted &&
            unit.currentMovement > 0 && unit.attacksThisTurn == 0

    private val tree = PromotionTree(unit)

    init {
        setDefaultCloseAction()

        if (canPromoteNow) {
            rightSideButton.setText("Pick promotion".tr())
            rightSideButton.onClick(UncivSound.Silent) {
                acceptPromotion(selectedPromotion)
            }
        } else {
            rightSideButton.isVisible = false
        }

        updateDescriptionLabel()

        if (canChangeState) {
            //Always allow the user to rename the unit as many times as they like.
            val renameButton = "Choose name for [${unit.name}]".toTextButton()
            renameButton.onClick {
                UnitRenamePopup(this, unit) {
                    game.replaceCurrentScreen(recreate())
                }
            }
            topTable.add(renameButton).pad(5f).row()
        }

        fillTable()

        displayTutorial(TutorialTrigger.Experience)
    }

    private fun acceptPromotion(button: PromotionButton?) {
        // if user managed to click disabled button, still do nothing
        if (button == null || !button.isPickable) return

        val path = tree.getPathTo(button.node.promotion)
        val oneSound = Actions.run { SoundPlayer.play(UncivSound.Promote) }
        stage.addAction(
            if (path.size == 1) oneSound
            else Actions.sequence(oneSound, Actions.delay(0.2f), oneSound )
        )

        for (promotion in path)
            unit.promotions.addPromotion(promotion.name)
        game.replaceCurrentScreen(recreate())
    }

    private fun fillTable() {
        // Create cell matrix
        val maxColumns = tree.getMaxColumns()
        val maxRows = tree.getMaxRows()

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

        /** Recursively place buttons for node and its successors into free cells */
        fun placeButton(col: Int, row: Int, node: PromotionTree.PromotionNode) : Int {
            val name = node.promotion.name
            // If promotion button not yet placed
            if (promotionToButton[name] == null) {
                // If place is free - we place button
                if (isTherePlace(row, col, node.depth)) {
                    val cell = cellMatrix[row][col]
                    val button = getButton(tree, node)
                    promotionToButton[name] = button
                    cell.setActor(button)
                        .pad(5f).padRight(20f).minWidth(190f).maxWidth(190f)
                }
                // If place is not free - try to find another in the next row
                else {
                    return placeButton(col, row+1, node)
                }
            }

            // Filter successors who haven't been placed yet (to avoid circular dependencies)
            // and try to place them in the next column.
            // Return the max row this whole tree ever reached.
            return node.children.filter {
                !promotionToButton.containsKey(it.promotion.name)
            }.map {
                placeButton(col+1, row, it)
            }.maxOfOrNull { it }?: row
        }

        // Build each tree starting from root nodes
        var row = 0
        for (node in tree.allRoots()) {
            row = placeButton(0, row, node)
            // Each root tree should start from a completely empty row.
            row += 1
        }

        topTable.add(promotionsTable)

        addConnectingLines()

    }

    private fun getButton(tree: PromotionTree, node: PromotionTree.PromotionNode) : PromotionButton {
        val isPickable = node.pathIsUnique && tree.canBuyUpTo(node.promotion)

        val button = PromotionButton(node, isPickable, promotedLabelStyle)

        button.onClick {
            selectedPromotion = button

            val path = tree.getPathTo(button.node.promotion)
            val pathAsSet = path.toSet()
            val prerequisites = button.node.parents

            for (btn in promotionToButton.values)
                btn.updateColor(btn == selectedPromotion, pathAsSet, prerequisites)

            rightSideButton.isEnabled = isPickable
            rightSideButton.setText(node.promotion.name.tr())
            updateDescriptionLabel(isPickable, tree, node, path)

            addConnectingLines()
        }

        if (isPickable)
            button.onDoubleClick(UncivSound.Silent) {
                acceptPromotion(button)
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
                    button == selectedPromotion -> buttonColors.selected
                    prerequisiteButton.node.baseName == button.node.baseName -> Color.WHITE.cpy()
                    else -> Color.CLEAR
                }
                val lineSize = if (button == selectedPromotion) 4f else 2f

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
            if (line.color == buttonColors.selected)
                line.zIndex = lines.size
        }
    }

    private fun setScrollY(scrollY: Float) {
        splitPane.pack()    // otherwise scrollPane.maxY == 0
        scrollPane.scrollY = scrollY
        scrollPane.updateVisualScroll()
    }

    private fun updateDescriptionLabel() {
        descriptionLabel.setText(unit.displayName().tr())
    }

    private fun updateDescriptionLabel(
        isPickable: Boolean,
        tree: PromotionTree,
        node: PromotionTree.PromotionNode,
        path: List<Promotion>
    ) {
        val isAmbiguous = tree.canBuyUpTo(node.promotion) && !node.pathIsUnique
        val topLine = unit.displayName().tr() + when {
            node.isAdopted -> ""
            isAmbiguous -> "{ }{- Path to [${node.promotion.name}] is ambiguous}".tr()
            !isPickable -> ""
            else -> path.joinToString(" → ", ": ") { it.name.tr() }
        }
        val promotionText = node.promotion.getDescription(tree.possiblePromotions)
        descriptionLabel.setText("$topLine\n$promotionText")
    }

    override fun recreate(): BaseScreen {
        val newScreen = PromotionPickerScreen(unit)
        newScreen.setScrollY(scrollPane.scrollY)
        return newScreen
    }
}
