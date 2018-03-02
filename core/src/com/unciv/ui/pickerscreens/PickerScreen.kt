package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.unciv.ui.utils.CameraStageBaseScreen

open class PickerScreen : CameraStageBaseScreen() {

    internal var closeButton: TextButton
    protected var descriptionLabel: Label
    protected var rightSideButton: TextButton
    internal var screenSplit = 0.85f
    protected var topTable: Table
    internal var splitPane: SplitPane

    init {
        val buttonTable = Table()

        closeButton = TextButton("Close", CameraStageBaseScreen.skin)
        closeButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                game.setWorldScreen()
                dispose()
            }
        })
        buttonTable.add(closeButton).width(stage.width / 4)

        descriptionLabel = Label("", CameraStageBaseScreen.skin)
        descriptionLabel.setWrap(true)
        descriptionLabel.setFontScale(game.settings.labelScale)
        descriptionLabel.width = stage.width / 2
        buttonTable.add(descriptionLabel).pad(5f).width(stage.width / 2)

        rightSideButton = TextButton("", CameraStageBaseScreen.skin)
        buttonTable.add(rightSideButton).width(stage.width / 4)
        buttonTable.height = stage.height * (1 - screenSplit)
        buttonTable.align(Align.center)
        rightSideButton.color = Color.GRAY
        rightSideButton.touchable = Touchable.disabled

        topTable = Table()
        val scrollPane = ScrollPane(topTable)

        scrollPane.setSize(stage.width, stage.height * screenSplit)

        splitPane = SplitPane(scrollPane, buttonTable, true, CameraStageBaseScreen.skin)
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

