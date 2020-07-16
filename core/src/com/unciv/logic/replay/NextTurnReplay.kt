package com.unciv.logic.replay

import com.unciv.logic.civilization.CivilizationInfo

/**
 * Replays recorded actions for each civilizations per turn
 * Would be used in GameInfo.NextTurn
 */
object NextTurnReplay {
    fun replayCivMoves(turn: Int, civInfo: CivilizationInfo) {
        val replay = Replay()
        val actions = replay.getCivActionsPerTurn(turn, civInfo)
        for (action in actions) {
            ActionMapper.apply(civInfo, action)
        }
    }
}