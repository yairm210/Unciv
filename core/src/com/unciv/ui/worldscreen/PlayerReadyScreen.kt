package com.unciv.ui.worldscreen

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.*

class PlayerReadyScreen(gameInfo: GameInfo, currentPlayerCiv: CivilizationInfo) : BaseScreen(){
    init {
        val table= Table()
        table.touchable= Touchable.enabled
        table.background= ImageGetter.getBackground(currentPlayerCiv.nation.getOuterColor())

        table.add("[$currentPlayerCiv] ready?".toLabel(currentPlayerCiv.nation.getInnerColor(),  Constants.headingFontSize))

        table.onClick {
            postCrashHandlingRunnable { // To avoid ANRs on Android when the creation of the worldscreen takes more than 500ms
                game.worldScreen = WorldScreen(gameInfo, currentPlayerCiv)
                game.setWorldScreen()
            }
        }
        table.setFillParent(true)
        stage.addActor(table)
    }
}
