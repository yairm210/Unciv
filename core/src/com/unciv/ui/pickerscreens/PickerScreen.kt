package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.utils.Align
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.disable

open class PickerScreen : CameraStageBaseScreen() {

    internal var closeButton: TextButton = TextButton("Close", CameraStageBaseScreen.skin)
    protected var descriptionLabel: Label
    protected var rightSideGroup = VerticalGroup()
    protected var rightSideButton: TextButton
    private var screenSplit = 0.85f
    protected var topTable: Table
    var bottomTable:Table = Table()
    internal var splitPane: SplitPane

    init {

        closeButton.addClickListener {
                game.setWorldScreen()
                dispose()
            }
        bottomTable.add(closeButton).width(stage.width / 4)

        descriptionLabel = Label("", CameraStageBaseScreen.skin)
        descriptionLabel.setWrap(true)
        descriptionLabel.setFontScale(game.settings.labelScale)
        descriptionLabel.width = stage.width / 2
        bottomTable.add(descriptionLabel).pad(5f).width(stage.width / 2)

        rightSideButton = TextButton("", CameraStageBaseScreen.skin)
        rightSideGroup.addActor(rightSideButton)
        bottomTable.add(rightSideGroup).width(stage.width / 4)
        bottomTable.height = stage.height * (1 - screenSplit)
        bottomTable.align(Align.center)
        rightSideButton.disable()

        topTable = Table()
        val scrollPane = ScrollPane(topTable)

        scrollPane.setSize(stage.width, stage.height * screenSplit)

        splitPane = SplitPane(scrollPane, bottomTable, true, CameraStageBaseScreen.skin)
        splitPane.setSplitAmount(screenSplit)
        splitPane.setFillParent(true)
        stage.addActor(splitPane)
    }

    protected fun pick(rightButtonText: String) {
        rightSideButton.touchable = Touchable.enabled
        rightSideButton.color = Color.WHITE
        rightSideButton.setText(rightButtonText)
    }
}

