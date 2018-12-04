package com.unciv.ui.pickerscreens

import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.utils.Align
import com.unciv.ui.utils.*

open class PickerScreen : CameraStageBaseScreen() {

    internal var closeButton: TextButton = TextButton("Close".tr(), CameraStageBaseScreen.skin)
    protected var descriptionLabel: Label
    protected var rightSideGroup = VerticalGroup()
    protected var rightSideButton: TextButton
    private var screenSplit = 0.85f
    protected var topTable: Table
    var bottomTable:Table = Table()
    internal var splitPane: SplitPane

    init {
        closeButton.onClick {
                game.setWorldScreen()
                dispose()
            }
        bottomTable.add(closeButton).width(stage.width / 4)

        descriptionLabel = Label("", CameraStageBaseScreen.skin)
        descriptionLabel.setWrap(true)
        val labelScroll = ScrollPane(descriptionLabel)
        bottomTable.add(labelScroll).pad(5f).width(stage.width / 2)

        rightSideButton = TextButton("", CameraStageBaseScreen.skin)
        rightSideButton.disable()
        rightSideGroup.addActor(rightSideButton)

        bottomTable.add(rightSideGroup).width(stage.width / 4)
        bottomTable.height = stage.height * (1 - screenSplit)
        bottomTable.align(Align.center)

        topTable = Table()
        val scrollPane = ScrollPane(topTable)

        scrollPane.setSize(stage.width, stage.height * screenSplit)

        splitPane = SplitPane(scrollPane, bottomTable, true, CameraStageBaseScreen.skin)
        splitPane.splitAmount = screenSplit
        splitPane.setFillParent(true)
        stage.addActor(splitPane)
    }

    protected fun pick(rightButtonText: String) {
        rightSideButton.enable()
        rightSideButton.setText(rightButtonText)
    }
}