package com.unciv.ui.popups

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GUI
import com.unciv.logic.civilization.managers.TurnManager
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.ui.screens.worldscreen.status.NextTurnButton

//todo Check move/top/end for "place one improvement" buildings
//todo Check add/remove-all for "place one improvement" buildings

/**
 * Adds a number of options 
 */
class AutoPlayMenu(
    stage: Stage,
    positionNextTo: Actor,
    private val nextTurnButton: NextTurnButton,
    private val worldScreen: WorldScreen
) : AnimatedMenuPopup(stage, getActorTopRight(positionNextTo)) {
    private val settings = GUI.getSettings()

    init {
        closeListeners.add {
        }
    }

    override fun createContentTable(): Table? {
        val table = super.createContentTable()!!
        table.add(getButton("AutoPlay End Turn", KeyboardBinding.RaisePriority, ::autoPlayEndTurn)).row()
        table.add(getButton("AutoPlay", KeyboardBinding.RaisePriority, ::autoPlay)).row()
        table.add(getButton("AutoPlay Military Once", KeyboardBinding.RaisePriority, ::autoPlayMilitary)).row()
        table.add(getButton("AutoPlay Civilians Once", KeyboardBinding.RaisePriority, ::autoPlayCivilian)).row()
        table.add(getButton("AutoPlay Economy Once", KeyboardBinding.RaisePriority, ::autoPlayEconomy)).row()

        return table.takeUnless { it.cells.isEmpty }
    }
    
    private fun autoPlayEndTurn() {
        TurnManager(worldScreen.viewingCiv).automateTurn()
        worldScreen.nextTurn()
    }

    private fun autoPlay() {
        settings.turnsToAutoPlay = settings.autoPlayMaxTurns
        nextTurnButton.update()
    }
    
    private fun autoPlayMilitary() {
        //TODO: add logic here
    }

    private fun autoPlayCivilian() {
        //TODO: add logic here
    }
    private fun autoPlayEconomy() {
        //TODO: add logic here
    }
}
