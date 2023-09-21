package com.unciv.ui.screens.worldscreen

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.screens.basescreen.BaseScreen

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

        table.onActivation {
            game.replaceCurrentScreen(worldScreen)
        }
        // Doing this separately instead of passing the binding to onActivation avoids the tooltip
        table.keyShortcuts.add(KeyboardBinding.NextTurnAlternate)
        table.setFillParent(true)
        stage.addActor(table)
    }
}
