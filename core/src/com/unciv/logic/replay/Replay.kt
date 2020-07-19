package com.unciv.logic.replay

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.Scenario

/**
 * Stores an [List] of state-changing actions for each civ per turn
 */
class Replay {

    var initialState = Scenario()

    var actions = HashMap<String, ArrayList<ArrayList<Action>>>()

    fun getCivActionsPerTurn(civInfo: CivilizationInfo, turn: Int): ArrayList<Action> {
        val actions = actions[civInfo.civName]!![turn]
        return actions
    }
}