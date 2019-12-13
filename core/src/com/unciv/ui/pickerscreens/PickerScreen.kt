package com.unciv.ui.pickerscreens

import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.UncivGame
import com.unciv.models.ruleset.tr
import com.unciv.ui.utils.*

open class PickerScreen : CameraStageBaseScreen() {

    internal var closeButton: TextButton = TextButton("Close".tr(), skin)
    protected var descriptionLabel: Label
    protected var rightSideGroup = VerticalGroup()
    protected var rightSideButton: TextButton
    private var screenSplit = 0.85f
    protected var topTable: Table
    var bottomTable:Table = Table()
    internal var splitPane: SplitPane
    protected var scrollPane: ScrollPane

    init {
        bottomTable.add(closeButton).pad(10f)

        descriptionLabel = "".toLabel()
        descriptionLabel.setWrap(true)
        val labelScroll = ScrollPane(descriptionLabel)
        bottomTable.add(labelScroll).pad(5f).fill().expand()

        rightSideButton = TextButton("", skin)
        rightSideButton.disable()
        rightSideGroup.addActor(rightSideButton)

        bottomTable.add(rightSideGroup).pad(10f).right()
        bottomTable.height = stage.height * (1 - screenSplit)

        topTable = Table()
        scrollPane = ScrollPane(topTable)

        scrollPane.setSize(stage.width, stage.height * screenSplit)

        splitPane = SplitPane(scrollPane, bottomTable, true, skin)
        splitPane.splitAmount = screenSplit
        splitPane.setFillParent(true)
        stage.addActor(splitPane)
    }

    fun setDefaultCloseAction() {
        closeButton.onClick {
            game.setWorldScreen()
            dispose()
        }
    }

    protected fun pick(rightButtonText: String) {
        if(UncivGame.Current.worldScreen.isPlayersTurn) rightSideButton.enable()
        rightSideButton.setText(rightButtonText)
    }
}