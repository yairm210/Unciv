package com.unciv.ui.screens.worldscreen.topbar

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.setFontSize
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.extensions.setLayer
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.onRightClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.overviewscreen.EmpireOverviewCategories
import com.unciv.ui.screens.worldscreen.BackgroundActor
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.ui.screens.worldscreen.mainmenu.WorldScreenMenuPopup
import kotlin.math.max


/**
 * Table consisting of the menu button, current civ, some stats and the overview button for the top of [WorldScreen].
 *
 * Calling [update] will refresh content and layout, and place the Table on the top edge of the stage, filling its width.
 *
 * [update] will also attempt geometry optimization:
 *  * When there's enough room, the top bar has the stats row ([WorldScreenTopBarStats]) and the resources
 *      row ([WorldScreenTopBarResources]), and the selected-civ ([SelectedCivilizationTable]) and overview
 *      ([OverviewAndSupplyTable]) button elements are overlaid (floating, not in a Cell) to the left and right.
 *  * When screen space gets cramped (low resolution or portrait mode) and one of the overlaid elements would
 *      cover parts of the stats and/or resources lines, we move them down accordingly - below the stats line
 *      if the resources still have enough room, below both otherwise.
 *  * But the elements should have a background - this is done with "filler cells". This Table is now 3x3,
 *      with the stats line as colspan(3) in the top row, resources also colspan(3) in the second row,
 *      and the third row is filler - empty - filler. These fillers do a background with just one rounded
 *      corner - bottom and to the screen center. The middle cell of that row has no actor and expands,
 *      and since the entire Table is Touchable.childrenOnly, completely transparent to the map below.
 *
 * Table layout in the "cramped" case:
 * ```
 * +----------------------------------------+
 * | WorldScreenTopBarStats      colspan(3) |
 * +----------------------------------------+
 * | WorldScreenTopBarResources  colspan(3) |
 * +----------------------------------------+
 * | Filler |    transparent!!!    | Filler |
 * +--------╝                      ╚--------+
 * ```
 * Reminder: Not the `Table`, the `Cell` actors (all except the transparent one) have the background.
 * To avoid gaps, _no_ padding except inside the cell actors, and all actors need to _fill_ their cell.
 */

//region Fields
class WorldScreenTopBar(internal val worldScreen: WorldScreen) : Table() {

    private val statsTable = WorldScreenTopBarStats(this)
    private val resourceTable = WorldScreenTopBarResources(this)
    private val selectedCivTable = SelectedCivilizationTable(worldScreen)
    private val overviewButton = OverviewAndSupplyTable(worldScreen)
    private val leftFiller: BackgroundActor
    private val rightFiller: BackgroundActor
    private var baseHeight = 0f

    companion object {
        /** When the "fillers" are used, this is added to the required height, alleviating the "gap" problem a little. */
        const val gapFillingExtraHeight = 1f
    }
    //endregion

    init {
        // init only prepares, the cells are created by update()

        defaults().center()
        setRound(false) // Prevent Table from doing internal rounding which would provoke gaps

        setLayer()

        // val backColor = BaseScreen.skin.getColor("base-40")
        // statsTable.background = BaseScreen.skinStrings.getUiBackground("WorldScreen/TopBar/StatsTable", tintColor = backColor)
        // resourceTable.background = BaseScreen.skinStrings.getUiBackground("WorldScreen/TopBar/ResourceTable", tintColor = backColor)

        //val leftFillerBG = BaseScreen.skinStrings.getUiBackground("WorldScreen/TopBar/LeftAttachment", BaseScreen.skinStrings.roundedEdgeRectangleShape, backColor)
        leftFiller = BackgroundActor(getBackground(), Align.topLeft)
        //val rightFillerBG = BaseScreen.skinStrings.getUiBackground("WorldScreen/TopBar/RightAttachment", BaseScreen.skinStrings.roundedEdgeRectangleShape, backColor)
        rightFiller = BackgroundActor(getBackground(), Align.topRight)
    }

    internal fun update(civInfo: Civilization) {
        setLayoutEnabled(false)
        statsTable.update(civInfo)
        resourceTable.update(civInfo)
        selectedCivTable.update(worldScreen)
        overviewButton.update(worldScreen)
        updateLayout()
        setLayoutEnabled(true)
    }

    internal fun getYForTutorialTask(): Float = y + height - baseHeight

    /** Performs the layout tricks mentioned in the class Kdoc */
    private fun updateLayout() {
        val targetWidth = stage.width
        val statsWidth = statsTable.prefWidth
        val resourceWidth = resourceTable.prefWidth
        val overviewWidth = overviewButton.minWidth
        val overviewHeight = overviewButton.minHeight
        val selectedCivWidth = selectedCivTable.minWidth
        val selectedCivHeight = selectedCivTable.minHeight
        // Since stats/resource lines are centered, the max decides when to snap the overlaid elements down
        val leftRightNeeded = max(selectedCivWidth, overviewWidth)
        // Height of the two "overlay" elements should be equal, but just in case:
        val overlayHeight = max(overviewHeight, selectedCivHeight)

        clear()
        // Without the explicit cell width, a 'stats' line wider than the stage can force the Table to
        // misbehave and place the filler actors out of bounds, even if Table.width is correct.
        add(statsTable).colspan(3).growX().width(targetWidth).row()
        // Probability of a too-wide resources line is low in Vanilla, but mods may have lots more...
        add(resourceTable).colspan(3).growX().width(targetWidth).row()
        layout()  // force rowHeight calculation - validate is not enough - Table quirks
        val statsRowHeight = getRowHeight(0)
        baseHeight = statsRowHeight + getRowHeight(1)

        fun addFillers(fillerHeight: Float) {
            add(leftFiller).size(selectedCivWidth, fillerHeight + gapFillingExtraHeight)
            add().growX()
            add(rightFiller).size(overviewWidth, fillerHeight + gapFillingExtraHeight)
        }

        // Check whether it gets cramped on narrow aspect ratios
        val centerButtonsToHeight = when {
            leftRightNeeded * 2f > targetWidth - resourceWidth -> {
                // Need to shift buttons down to below both stats and resources
                addFillers(overlayHeight)
                overlayHeight
            }
            leftRightNeeded * 2f > targetWidth - statsWidth -> {
                // Shifting buttons down to below stats row is enough
                addFillers(statsRowHeight)
                overlayHeight
            }
            else -> {
                // Enough space to keep buttons to the left and right of stats and resources - no fillers
                baseHeight
            }
        }

        // Don't use align with setPosition as we haven't pack()ed and element dimensions might not be final
        setSize(targetWidth, prefHeight)  // sizing to prefHeight is half a pack()
        setPosition(0f, stage.height - prefHeight)

        selectedCivTable.setPosition(0f, (centerButtonsToHeight - selectedCivHeight) / 2f)
        overviewButton.setPosition(targetWidth - overviewWidth, (centerButtonsToHeight - overviewHeight) / 2f)
        addActor(selectedCivTable) // needs to be after size
        addActor(overviewButton)
    }

    private class OverviewAndSupplyTable(worldScreen: WorldScreen) : Table(BaseScreen.skin) {
        val unitSupplyImage = ImageGetter.getImage("OtherIcons/ExclamationMark")
            .apply { color = Color.FIREBRICK }
        val unitSupplyCell: Cell<Actor?>

        init {
            unitSupplyImage.onClick {
                worldScreen.openEmpireOverview(EmpireOverviewCategories.Units)
            }

            val overviewButton = "Overview".toTextButton()
            overviewButton.onActivation(binding = KeyboardBinding.EmpireOverview) {
                worldScreen.openEmpireOverview()
            }

            unitSupplyCell = add()
            add(overviewButton).padRight(Fonts.rem(0.5f))
            pack()
        }

        fun update(worldScreen: WorldScreen) {
            val newVisible = worldScreen.selectedCiv.stats.getUnitSupplyDeficit() > 0
            if (newVisible == unitSupplyCell.hasActor()) return
            if (newVisible) unitSupplyCell.setActor(unitSupplyImage)
                .size(50f).padLeft(10f)
            else unitSupplyCell.setActor(null).size(0f).pad(0f)
            invalidate()
            pack()
        }
    }

    private class SelectedCivilizationTable(worldScreen: WorldScreen) : Table(BaseScreen.skin) {
        private var selectedCiv = ""
        // Instead of allowing tr() to insert the nation icon - we don't want it scaled with fontSizeMultiplier
        private var selectedCivIcon = Group()
        private val selectedCivIconCell: Cell<Group>
        private val selectedCivLabel = "".toLabel()

        private val menuButton = ImageGetter.getImage("OtherIcons/MenuIcon")
        private val menuButtonWrapper = Container(menuButton)

        init {
            left()
            pad(10f)

            menuButton.color = Color.WHITE
            menuButton.onActivation(binding = KeyboardBinding.Menu) { WorldScreenMenuPopup(worldScreen) }
            menuButton.onRightClick { WorldScreenMenuPopup(worldScreen, true) }

            val onNationClick = {
                worldScreen.openCivilopedia(worldScreen.selectedCiv.nation.makeLink())
            }

            selectedCivLabel.setFontSize(Constants.headingFontSize)
            selectedCivLabel.onClick(onNationClick)
            selectedCivIcon.onClick(onNationClick)

            menuButtonWrapper.size(Constants.headingFontSize * 1.5f);
            menuButtonWrapper.center()
            add(menuButtonWrapper)

            selectedCivIconCell = add(selectedCivIcon).padLeft(Constants.defaultFontSize / 1.5f)
            add(selectedCivLabel).padTop(10f - Fonts.getDescenderHeight(Constants.headingFontSize))
                .padLeft(Constants.defaultFontSize / 2.0f)
            pack()
        }

        fun update(worldScreen: WorldScreen) {
            val newCiv = worldScreen.selectedCiv.civName
            if (this.selectedCiv == newCiv) return
            this.selectedCiv = newCiv

            selectedCivIcon = ImageGetter.getNationPortrait(worldScreen.selectedCiv.nation, 25f)
            selectedCivIconCell.setActor(selectedCivIcon)
            selectedCivLabel.setText(newCiv.tr(hideIcons = true))
            invalidate()
            pack()
        }
    }
}
