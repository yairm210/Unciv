package com.unciv.logic.replay

import com.unciv.logic.civilization.CivilizationInfo

/**
 * Stores an [ArrayList] of state-changing actions for each civ per turn
 */
class Replay {
    var actions = HashMap<String, ArrayList<ArrayList<Action>>>()

    fun getCivActionsPerTurn(turn: Int, civInfo: CivilizationInfo): ArrayList<Action> {
        val actions = actions[civInfo.civName]!![turn]
        return actions
    }
}