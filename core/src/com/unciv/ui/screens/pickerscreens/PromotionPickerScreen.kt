package com.unciv.ui.screens.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
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
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.onDoubleClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.RecreateOnResize
import com.unciv.ui.screens.civilopediascreen.CivilopediaScreen
import kotlin.math.abs

class PromotionPickerScreen(
    val unit: MapUnit,
    private val closeOnPick: Boolean = true,
    private val onChange: (() -> Unit)? = null
) : PickerScreen(), RecreateOnResize {
    // Style stuff
    private val colors = skin[PromotionScreenColors::class.java]
    private val promotedLabelStyle = Label.LabelStyle(skin[Label.LabelStyle::class.java]).apply {
        fontColor = colors.promotedText
    }
    private val buttonCellMaxWidth: Float
    private val buttonCellMinWidth: Float

    // Widgets
    private val promotionsTable = Table()
    private val promotionToButton = LinkedHashMap<String, PromotionButton>()
    private var selectedPromotion: PromotionButton? = null
    private var lines = ArrayList<Image>()

    // [acceptPromotion] will [recreate] the screen, so these are constant for this picker's lifetime
    private val canChangeState = GUI.isAllowedChangeState()
    private val canPromoteNow = canChangeState &&
            unit.promotions.canBePromoted() &&
            unit.currentMovement > 0 && unit.attacksThisTurn == 0

    // Logic
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

        // Create all buttons without placing them yet, measure
        buttonCellMaxWidth = ((stage.width - 80f) / tree.getMaxColumns())
            .coerceIn(190f, 300f)
        for (node in tree.allNodes())
            promotionToButton[node.promotion.name] = getButton(tree, node)
        buttonCellMinWidth = (promotionToButton.values.maxOfOrNull { it.prefWidth + 10f } ?: 0f)
            .coerceIn(190f, buttonCellMaxWidth)

        fillTable()

        displayTutorial(TutorialTrigger.Experience)
    }

    private fun acceptPromotion(button: PromotionButton?) {
        // if user managed to click disabled button, still do nothing
        if (button == null || !button.isPickable) return

        val path = tree.getPathTo(button.node.promotion)
        SoundPlayer.playRepeated(UncivSound.Promote, path.size.coerceAtMost(2))

        for (promotion in path)
            unit.promotions.addPromotion(promotion.name)

        onChange?.invoke()

        if (!closeOnPick || unit.promotions.canBePromoted())
            game.replaceCurrentScreen(recreate(false))
        else
            game.popScreen()
    }

    private fun fillTable() {
        val placedButtons = mutableSetOf<String>()

        // Create cell matrix
        val maxColumns = tree.getMaxColumns()
        val maxRows = tree.getMaxRows()
        val cellMatrix = Array(maxRows + 1) {
            Array(maxColumns + 1) {
                promotionsTable.add() as Cell<Actor?>
            }.also {
                promotionsTable.row()
            }
        }

        /** Check whether a horizontal range of cells is inhabited by any actor already */
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
            if (name !in placedButtons) {
                // If place is free - we place button
                if (isTherePlace(row, col, node.levels)) {
                    cellMatrix[row][col].setActor(promotionToButton[name])
                        .pad(5f).padRight(20f)
                        .minWidth(buttonCellMinWidth).maxWidth(buttonCellMaxWidth)
                    placedButtons += name
                }
                // If place is not free - try to find another in the next row
                else {
                    return placeButton(col, row+1, node)
                }
            }

            // Filter children who haven't been placed yet (to avoid circular dependencies)
            // and try to place them in the next column.
            // Note this materializes all intermediaries as Lists, but they're small
            // Also note having placeButton with nointrivial side effecths in a chain isn't good practice,
            // But the alternative is coding the max manually.
            // Return the max row this whole tree ever reached.
            return node.children
                .filter { it.promotion.name !in placedButtons }
                .sortedBy { it.baseName != node.baseName }  // Prioritize getting groups in a row - relying on sensible json "column" isn't enough
                .maxOfOrNull { placeButton(col + 1, row, it) }
                ?: row
        }

        // Build each tree starting from root nodes
        var row = 0
        for (node in tree.allRoots()) {
            row = placeButton(0, row, node)
            // Each root tree should start from a completely empty row.
            row += 1
        }

        topTable.add(promotionsTable)
        addConnectingLines(emptySet())
    }

    private fun getButton(tree: PromotionTree, node: PromotionTree.PromotionNode) : PromotionButton {
        val isPickable = canPromoteNow &&
            (!node.pathIsAmbiguous || node.distanceToAdopted == 1) &&
            tree.canBuyUpTo(node.promotion)

        val button = PromotionButton(node, isPickable, promotedLabelStyle, buttonCellMaxWidth - 60f)

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

            addConnectingLines(pathAsSet)
        }

        if (isPickable)
            button.onDoubleClick(UncivSound.Silent) {
                acceptPromotion(button)
            }

        return button
    }

    private fun addConnectingLines(path: Set<Promotion>) {
        promotionsTable.pack()
        scrollPane.updateVisualScroll()

        for (line in lines) line.remove()
        lines.clear()

        fun addLine(x: Float, y: Float, width: Float, height: Float, color: Color) {
            if (color.a == 0f) return
            val line = ImageGetter.getWhiteDot()
            line.setBounds(x, y, width, height)
            line.color = color
            promotionsTable.addActorAt(0, line)
            lines.add(line)
        }

        for (button in promotionToButton.values) {
            val currentNode = button.node
            for (prerequisite in currentNode.promotion.prerequisites) {
                val prerequisiteButton = promotionToButton[prerequisite] ?: continue
                val prerequisiteNode = prerequisiteButton.node

                var buttonCoords = Vector2(0f, button.height / 2)
                button.localToStageCoordinates(buttonCoords)
                promotionsTable.stageToLocalCoordinates(buttonCoords)

                var prerequisiteCoords = Vector2(prerequisiteButton.width, prerequisiteButton.height / 2)
                prerequisiteButton.localToStageCoordinates(prerequisiteCoords)
                promotionsTable.stageToLocalCoordinates(prerequisiteCoords)

                val isNodeInPath = currentNode.promotion in path
                val isSelectionPath = isNodeInPath &&
                    (prerequisiteNode.isAdopted || prerequisiteNode.promotion in path)
                val lineColor = when {
                    isSelectionPath -> colors.selected
                    isNodeInPath -> colors.pathToSelection
                    prerequisiteNode.baseName == currentNode.baseName -> colors.groupLines
                    else -> colors.otherLines
                }
                val lineSize = if (isSelectionPath) 4f else 2f

                if (buttonCoords.x < prerequisiteCoords.x) {
                    val temp = buttonCoords.cpy()
                    buttonCoords = prerequisiteCoords
                    prerequisiteCoords = temp
                }

                val halfLineSize = lineSize / 2
                if (buttonCoords.y != prerequisiteCoords.y) {

                    val deltaX = buttonCoords.x - prerequisiteCoords.x
                    val deltaY = buttonCoords.y - prerequisiteCoords.y
                    val halfLength = deltaX / 2f + halfLineSize

                    addLine(
                        width = halfLength,
                        height = lineSize,
                        x = prerequisiteCoords.x,
                        y = prerequisiteCoords.y - halfLineSize,
                        color = lineColor
                    )
                    addLine(
                        width = halfLength,
                        height = lineSize,
                        x = buttonCoords.x - halfLength,
                        y = buttonCoords.y - halfLineSize,
                        color = lineColor
                    )
                    addLine(
                        width = lineSize,
                        height = abs(deltaY),
                        x = buttonCoords.x - halfLength,
                        y = buttonCoords.y + (if (deltaY > 0f) -deltaY - halfLineSize else halfLineSize),
                        color = lineColor
                    )
                } else {
                    addLine(
                        width = buttonCoords.x - prerequisiteCoords.x,
                        height = lineSize,
                        x = prerequisiteCoords.x,
                        y = prerequisiteCoords.y - halfLineSize,
                        color = lineColor
                    )
                }
            }
        }

        for (line in lines) {
            if (line.color == colors.selected || line.color == colors.pathToSelection)
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
        val isAmbiguous = node.pathIsAmbiguous && node.distanceToAdopted > 1 && tree.canBuyUpTo(node.promotion)
        val topLine = unit.displayName().tr() + when {
            node.isAdopted -> ""
            isAmbiguous -> " - {Path to [${node.promotion.name}] is ambiguous}".tr()
            !isPickable -> ""
            else -> path.joinToString(" → ", ": ") { it.name.tr() }
        }
        val promotionText = node.promotion.getDescription(tree.possiblePromotions)
        descriptionLabel.setText("$topLine\n$promotionText")
        descriptionLabel.clearListeners()
        descriptionLabel.onActivation {
            game.pushScreen(CivilopediaScreen(unit.baseUnit.ruleset, link = node.promotion.makeLink()))
        }
        descriptionLabel.keyShortcuts.add(KeyboardBinding.Civilopedia)
    }

    override fun recreate() = recreate(closeOnPick)

    fun recreate(closeOnPick: Boolean): BaseScreen {
        val newScreen = PromotionPickerScreen(unit, closeOnPick, onChange)
        newScreen.setScrollY(scrollPane.scrollY)
        return newScreen
    }
}
