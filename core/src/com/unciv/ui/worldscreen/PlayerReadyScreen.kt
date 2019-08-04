package com.unciv.ui.worldscreen

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UnCivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.ui.utils.*

class PlayerReadyScreen(currentPlayerCiv: CivilizationInfo) : CameraStageBaseScreen(){
    init {
        val table= Table()
        table.touchable= Touchable.enabled
        table.background= ImageGetter.getBackground(currentPlayerCiv.getNation().getColor())

        table.add("[$currentPlayerCiv] ready?".toLabel().setFontSize(24)
                .setFontColor(currentPlayerCiv.getNation().getSecondaryColor()))

        table.onClick {
            UnCivGame.Current.worldScreen = WorldScreen(currentPlayerCiv).apply {
                shouldUpdate = true
            }
            UnCivGame.Current.setWorldScreen()
        }
        table.setFillParent(true)
        stage.addActor(table)
    }
}