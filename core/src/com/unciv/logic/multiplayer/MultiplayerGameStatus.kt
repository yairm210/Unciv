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
class MultiplayerGameStatus() : HasGameId, HasGameTurnData {
    var civilizations = mutableListOf<CivilizationInfoPreview>()
    var difficulty = "Chieftain"
    var gameParameters = GameParameters()
    override var gameId = ""
    override var turns = 0
    override var currentPlayer = ""
    override var currentTurnStartTime = 0L

    /**
     * Converts a GameInfo object (can be uninitialized).
     * Sets all multiplayer settings to default.
     */
    constructor(gameInfo: GameInfo) : this() {
        civilizations = gameInfo.getCivilizationsAsPreviews()
        difficulty = gameInfo.difficulty
        gameParameters = gameInfo.gameParameters
        turns = gameInfo.turns
        gameId = gameInfo.gameId
        currentPlayer = gameInfo.currentPlayer
        currentTurnStartTime = gameInfo.currentTurnStartTime
    }

    fun getCivilization(civName: String) = civilizations.first { it.civName == civName }
}
