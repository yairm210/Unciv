package com.unciv.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Stage
import com.unciv.MainMenuScreen
import com.unciv.ui.utils.*

class TestDragScreen : CameraStageBaseScreen() {
    init {
        val backButton = "Back".toTextButton()
        backButton.onClick {
            game.setScreen(MainMenuScreen())
        }

        stage.addActor(backButton)

        val testRectangle = DragAndDropRectangle("DragTime".toLabel(), "tester".toLabel(), Color.GREEN, stage)
        stage.addActor(testRectangle)
    }
}