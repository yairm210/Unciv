package com.unciv.logic.replay

import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.Scenario

/**
 * Stores initial game state and a [List] of state-changing actions for each civ
 */
class Replay {

    var initialState = GameInfo()
    var finalState = GameInfo()

    var actions = HashMap<String, ArrayList<ArrayList<Action>>>()

    fun getCivActionsPerTurn(civInfo: CivilizationInfo, turn: Int): ArrayList<Action> {
        val actions = actions[civInfo.civName]!![turn]
        return actions
    }
}