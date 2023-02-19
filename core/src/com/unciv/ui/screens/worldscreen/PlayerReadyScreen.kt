package com.unciv.ui.screens.worldscreen

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.ui.components.BaseScreen
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toLabel

class PlayerReadyScreen(worldScreen: WorldScreen) : BaseScreen() {
    init {
        val table = Table()
        table.touchable = Touchable.enabled
        val curCiv = worldScreen.viewingCiv
        table.background = skinStrings.getUiBackground(
            "PlayerReadyScreen/Background",
            tintColor = curCiv.nation.getOuterColor()
        )

        table.add("[$curCiv] ready?".toLabel(curCiv.nation.getInnerColor(), Constants.headingFontSize))

        table.onClick {
            game.replaceCurrentScreen(worldScreen)
        }
        table.setFillParent(true)
        stage.addActor(table)
    }
}
