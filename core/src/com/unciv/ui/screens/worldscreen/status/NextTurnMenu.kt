package com.unciv.ui.screens.worldscreen.status

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.popups.AnimatedMenuPopup
import com.unciv.ui.screens.worldscreen.WorldScreen

class NextTurnMenu(
    stage: Stage,
    positionNextTo: Actor,
    private val nextTurnButton: NextTurnButton,
    private val worldScreen: WorldScreen
) : AnimatedMenuPopup(stage, getActorTopRight(positionNextTo)) {
    override fun createContentTable(): Table {
        val table = super.createContentTable()!!
        table.add(getButton("Next Turn", KeyboardBinding.NextTurn) { worldScreen.nextTurn() }).row()

        return table
    }
}