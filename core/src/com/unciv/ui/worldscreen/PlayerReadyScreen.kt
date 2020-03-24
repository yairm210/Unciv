package com.unciv.ui.worldscreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel

class PlayerReadyScreen(currentPlayerCiv: CivilizationInfo) : CameraStageBaseScreen(){
    init {
        val table= Table()
        table.touchable= Touchable.enabled
        table.background= ImageGetter.getBackground(currentPlayerCiv.nation.getOuterColor())

        table.add("[$currentPlayerCiv] ready?".toLabel(currentPlayerCiv.nation.getInnerColor(),24))

        table.onClick {
            Gdx.app.postRunnable { // To avoid ANRs on Android when the creation of the worldscreen takes more than 500ms
                UncivGame.Current.worldScreen = WorldScreen(currentPlayerCiv)
                UncivGame.Current.setWorldScreen()
            }
        }
        table.setFillParent(true)
        stage.addActor(table)
    }
}