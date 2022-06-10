package com.unciv.ui.worldscreen

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toLabel

class PlayerReadyScreen(worldScreen: WorldScreen) : BaseScreen() {
    init {
        val table = Table()
        table.touchable = Touchable.enabled
        val curCiv = worldScreen.viewingCiv
        table.background = ImageGetter.getBackground(curCiv.nation.getOuterColor())

        table.add("[$curCiv] ready?".toLabel(curCiv.nation.getInnerColor(), Constants.headingFontSize))

        table.onClick {
            postCrashHandlingRunnable { // To avoid ANRs on Android when the creation of the worldscreen takes more than 500ms
                game.setScreen(worldScreen)
            }
        }
        table.setFillParent(true)
        stage.addActor(table)
    }
}
