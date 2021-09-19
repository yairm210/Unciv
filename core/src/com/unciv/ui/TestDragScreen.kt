package com.unciv.ui

import com.badlogic.gdx.graphics.Color
import com.unciv.MainMenuScreen
import com.unciv.ui.utils.*
import com.unciv.ui.utils.DragAndDropTools.DragAndDropRectangle

class TestDragScreen : CameraStageBaseScreen() {
    init {
        val backButton = "Back".toTextButton()
        backButton.onClick {
            game.setScreen(MainMenuScreen())
        }

        stage.addActor(backButton)

        val testRectangle = DragAndDropRectangle("DragTime".toLabel(), "tester".toLabel(), Color.GREEN)
        stage.addActor(testRectangle)
    }

}