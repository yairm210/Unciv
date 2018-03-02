package com.unciv.ui

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.utils.CameraStageBaseScreen

class VictoryScreen : CameraStageBaseScreen() {
    init {

        val table = Table()
        val label = Label("A resounding victory!", CameraStageBaseScreen.skin)
        label.setFontScale(2f)

        table.add(label).pad(20f).row()

        val newGameButton = TextButton("New game!", CameraStageBaseScreen.skin)
        newGameButton.addClickListener { game.startNewGame() }
        table.add(newGameButton).pad(20f).row()


        table.pack()
        table.setPosition((stage.width - table.width) / 2, (stage.height - table.height) / 2)

        stage.addActor(table)
    }


}
