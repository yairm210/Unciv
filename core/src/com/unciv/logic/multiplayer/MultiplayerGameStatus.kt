package com.unciv.logic.multiplayer

import com.unciv.logic.GameInfo
import com.unciv.logic.HasGameId
import com.unciv.logic.HasGameTurnData
import com.unciv.logic.civilization.CivilizationInfoPreview
import com.unciv.models.metadata.GameParameters

/**
 * Reduced variant of GameInfo used for multiplayer saves.
 * Contains additional data for multiplayer settings.
 */
class MultiplayerGameStatus private constructor() : HasGameId, HasGameTurnData {
    override var gameId = ""
    override var turns = 0
    override var currentCivName = ""
    override var currentTurnStartTime = 0L

    /**
     * Converts a GameInfo object (can be uninitialized).
     * Sets all multiplayer settings to default.
     */
    constructor(gameInfo: GameInfo) : this() {
        gameId = gameInfo.gameId
        turns = gameInfo.turns
        currentCivName = gameInfo.currentCivName
        currentPlayerId = gameInfo.getCivilization(currentCivName).playerId
        currentTurnStartTime = gameInfo.currentTurnStartTime
    }

    override fun toString(): String {
        return "MultiplayerGameStatus(gameId='$gameId', turns=$turns, currentCivName='$currentCivName', currentPlayerId='$currentPlayerId', currentTurnStartTime=$currentTurnStartTime)"
    }
}
