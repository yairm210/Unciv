package com.unciv.ui.pickerscreens

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.utils.AutoScrollPane
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.extensions.disable
import com.unciv.ui.utils.extensions.enable
import com.unciv.ui.utils.extensions.isEnabled
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton

class PickerPane(
    disableScroll: Boolean = false,
) : Table() {
    /** The close button on the lower left of [bottomTable], see [setDefaultCloseAction] */
    val closeButton = Constants.close.toTextButton()
    /** A scrollable wrapped Label you can use to show descriptions in the [bottomTable], starts empty */
    val descriptionLabel = "".toLabel()
    /** A wrapper containing [rightSideButton]. You can add buttons, they will be arranged vertically */
    val rightSideGroup = VerticalGroup()
    /** A button on the lower right of [bottomTable] you can use for a "OK"-type action, starts disabled */
    val rightSideButton = "".toTextButton()

    private val screenSplit = 0.85f
    private val maxBottomTableHeight = 150f     // about 7 lines of normal text

    /**
     * The table displaying the choices from which to pick (usually).
     * Also the element which most of the screen real estate is devoted to displaying.
     */
    val topTable = Table()
    /** Holds the [Close button][closeButton], a [description label][descriptionLabel] and an [action button][rightSideButton] */
    val bottomTable = Table()
    /** A ScrollPane scrolling [topTable], disabled by the disableScroll parameter */
    val scrollPane = AutoScrollPane(topTable)
    /** A fixed SplitPane holds [scrollPane] and [bottomTable] */
    val splitPane = SplitPane(scrollPane, bottomTable, true, BaseScreen.skin)

    init {
        bottomTable.add(closeButton).pad(10f)

        descriptionLabel.wrap = true
        val labelScroll = AutoScrollPane(descriptionLabel, BaseScreen.skin)
        bottomTable.add(labelScroll).pad(5f).fill().expand()

        rightSideButton.disable()
        rightSideGroup.addActor(rightSideButton)

        bottomTable.add(rightSideGroup).pad(10f).right()

        scrollPane.setScrollingDisabled(disableScroll, disableScroll)  // lock scrollPane
        if (disableScroll) scrollPane.clearListeners()  // remove focus capture of AutoScrollPane too
        add(splitPane).expand().fill()
    }

    override fun layout() {
        super.layout()
        bottomTable.height = bottomTable.height.coerceAtMost(maxBottomTableHeight)
        splitPane.splitAmount = (scrollPane.height / (scrollPane.height + bottomTable.height)).coerceAtLeast(screenSplit)
    }

    /** Enables the [rightSideButton]. See [pick] for a way to set the text. */
    fun setRightSideButtonEnabled(enabled: Boolean) {
        rightSideButton.isEnabled = enabled
    }

    /** Sets the text of the [rightSideButton] and enables it if it's the player's turn */
    fun pick(rightButtonText: String) {
        if (UncivGame.Current.worldScreen!!.isPlayersTurn) rightSideButton.enable()
        rightSideButton.setText(rightButtonText)
    }

    companion object {
        /** Icon size used in [getPickerOptionButton]. */
        const val pickerOptionIconSize = 30f
        /** Return a button for picker screens that display a list of big buttons with icons and labels. */
        fun getPickerOptionButton(icon: Actor, label: String): Button {
            return IconTextButton(label, icon).apply {
                iconCell!!.size(pickerOptionIconSize).pad(10f)
                labelCell.pad(10f)
            }
        }
    }
}
