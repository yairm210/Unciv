package com.unciv.ui.screens.worldscreen.status

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.automation.civilization.NextTurnAutomation
import com.unciv.logic.automation.unit.UnitAutomation
import com.unciv.logic.civilization.managers.TurnManager
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.popups.AnimatedMenuPopup
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.ui.screens.worldscreen.unit.AutoPlay
import com.unciv.utils.Concurrency

/**
 *  The "context" menu for the AutoPlay button
 */
class AutoPlayMenu(
    stage: Stage,
    positionNextTo: Actor,
    private val nextTurnButton: NextTurnButton,
    private val worldScreen: WorldScreen
) : AnimatedMenuPopup(stage, getActorTopRight(positionNextTo)) {

    private val autoPlay: AutoPlay = worldScreen.autoPlay

    init {
        // We need to activate the end turn button again after the menu closes
        afterCloseCallback = { worldScreen.shouldUpdate = true }
    }

    override fun createContentTable(): Table {
        val table = super.createContentTable()!!
        // Using the same keyboard binding for bypassing this menu and the default option
        if (!worldScreen.gameInfo.gameParameters.isOnlineMultiplayer)
            table.add(getButton("Start AutoPlay", KeyboardBinding.AutoPlay, ::multiturnAutoPlay)).row()
        table.add(getButton("AutoPlay End Turn", KeyboardBinding.AutoPlayMenuEndTurn, ::autoPlayEndTurn)).row()
        table.add(getButton("AutoPlay Military Once", KeyboardBinding.AutoPlayMenuMilitary, ::autoPlayMilitary)).row()
        table.add(getButton("AutoPlay Civilians Once", KeyboardBinding.AutoPlayMenuCivilians, ::autoPlayCivilian)).row()
        table.add(getButton("AutoPlay Economy Once", KeyboardBinding.AutoPlayMenuEconomy, ::autoPlayEconomy)).row()

        return table
    }

    private fun autoPlayEndTurn() {
        autoPlay.autoPlayTurnInProgress = true
        nextTurnButton.update()

        if (worldScreen.viewingCiv.units.getCivUnitsSize() + worldScreen.viewingCiv.cities.size >= 30) {

            Concurrency.runOnNonDaemonThreadPool("AutoPlayEndTurn") {
                TurnManager(worldScreen.viewingCiv).automateTurn()
                worldScreen.autoPlay.stopAutoPlay()
                worldScreen.nextTurn()
            }
        } else {
            TurnManager(worldScreen.viewingCiv).automateTurn()
            worldScreen.autoPlay.stopAutoPlay()
            worldScreen.nextTurn()
        }
    }

    private fun multiturnAutoPlay() {
        worldScreen.autoPlay.startMultiturnAutoPlay()
        nextTurnButton.update()
    }

    private fun autoPlayMilitary() {
        val civInfo = worldScreen.viewingCiv
        val isAtWar = civInfo.isAtWar()
        val sortedUnits = civInfo.units.getCivUnits().filter { it.isMilitary() }.sortedBy { unit -> NextTurnAutomation.getUnitPriority(unit, isAtWar) }
        for (unit in sortedUnits) UnitAutomation.automateUnitMoves(unit)

        for (city in civInfo.cities) UnitAutomation.tryBombardEnemy(city)
        worldScreen.shouldUpdate = true
        worldScreen.render(0f)
    }

    private fun autoPlayCivilian() {
        val civInfo = worldScreen.viewingCiv
        val isAtWar = civInfo.isAtWar()
        val sortedUnits = civInfo.units.getCivUnits().filter { it.isCivilian() }.sortedBy { unit -> NextTurnAutomation.getUnitPriority(unit, isAtWar) }
        for (unit in sortedUnits) UnitAutomation.automateUnitMoves(unit)
        worldScreen.shouldUpdate = true
        worldScreen.render(0f)
    }

    private fun autoPlayEconomy() {
        val civInfo = worldScreen.viewingCiv
        NextTurnAutomation.automateCities(civInfo)
        worldScreen.shouldUpdate = true
        worldScreen.render(0f)
    }
}
