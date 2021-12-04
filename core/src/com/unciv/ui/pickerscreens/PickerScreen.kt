package com.unciv.ui.pickerscreens

import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.ui.utils.*
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

open class PickerScreen(disableScroll: Boolean = false) : BaseScreen() {
    /** The close button on the lower left of [bottomTable], see [setDefaultCloseAction] */
    protected var closeButton: TextButton = Constants.close.toTextButton()
    /** A scrollable wrapped Label you can use to show descriptions in the [bottomTable], starts empty */
    protected var descriptionLabel: Label
    /** A wrapper containing [rightSideButton]. You can add buttons, they will be arranged vertically */
    protected var rightSideGroup = VerticalGroup()
    /** A button on the lower right of [bottomTable] you can use for a "OK"-type action, starts disabled */
    protected var rightSideButton: TextButton

    private val screenSplit = 0.85f
    private val maxBottomTableHeight = 150f     // about 7 lines of normal text

    /**
     * The table displaying the choices from which to pick (usually).
     * Also the element which most of the screen real estate is devoted to displaying.
     */
    protected var topTable: Table
    /** Holds the [Close button][closeButton], a [description label][descriptionLabel] and an [action button][rightSideButton] */
    protected var bottomTable:Table = Table()
    /** A fixed SplitPane holds [scrollPane] and [bottomTable] */
    protected var splitPane: SplitPane
    /** A ScrollPane scrolling [topTable], disabled by the disableScroll parameter */
    protected var scrollPane: ScrollPane

    init {
        bottomTable.add(closeButton).pad(10f)

        descriptionLabel = "".toLabel()
        descriptionLabel.wrap = true
        val labelScroll = ScrollPane(descriptionLabel, skin)
        bottomTable.add(labelScroll).pad(5f).fill().expand()

        rightSideButton = "".toTextButton()
        rightSideButton.disable()
        rightSideGroup.addActor(rightSideButton)

        bottomTable.add(rightSideGroup).pad(10f).right()
        bottomTable.height = (stage.height * (1 - screenSplit)).coerceAtMost(maxBottomTableHeight)

        topTable = Table()
        scrollPane = ScrollPane(topTable)

        scrollPane.setScrollingDisabled(disableScroll, disableScroll)  // lock scrollPane
        if (disableScroll) scrollPane.clearListeners()  // remove focus capture of AutoScrollPane too
        scrollPane.setSize(stage.width, stage.height - bottomTable.height)

        splitPane = SplitPane(scrollPane, bottomTable, true, skin)
        splitPane.splitAmount = scrollPane.height / stage.height
        splitPane.setFillParent(true)
        stage.addActor(splitPane)
    }

    /**
     * Initializes the [Close button][closeButton]'s action (and the Back/ESC handler)
     * to return to the [previousScreen] if specified, or else to the world screen.
     */
    fun setDefaultCloseAction(previousScreen: BaseScreen?=null) {
        val closeAction = {
            if (previousScreen != null) game.setScreen(previousScreen)
            else game.setWorldScreen()
            dispose()
        }
        closeButton.onClick(closeAction)
        onBackButtonClicked(closeAction)
    }

    /** Enables the [rightSideButton]. See [pick] for a way to set the text. */
    fun setRightSideButtonEnabled(enabled: Boolean) {
        rightSideButton.isEnabled = enabled
    }

    /** Sets the text of the [rightSideButton] and enables it if it's the player's turn */
    protected fun pick(rightButtonText: String) {
        if (UncivGame.Current.worldScreen.isPlayersTurn) rightSideButton.enable()
        rightSideButton.setText(rightButtonText)
    }

    /** Remove listeners from [rightSideButton] to prepare giving it a new onClick */
    fun removeRightSideClickListeners() {
        rightSideButton.clearListeners()
    }
}
