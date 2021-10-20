package com.unciv.logic

/**
 * MultiplayerGameInfo objects can be loaded/saved faster than GameInfo objects
 * and also hold additional multiplayer game settings
 */
data class MultiplayerGameInfo (
        var gameId: String = "",
        var currentPlayer: String = "",
        var turns: Int = 0,
        var turnNotification: Boolean = true,
        var currentPlayerId: String = "",
        ) {
    /**
     * Converts a GameInfo object (can be uninitialized) into a MultiplayerGameInfo object.
     * Sets all multiplayer settings to default.
     */
    constructor(gameInfo: GameInfo) : this() {
        gameId = gameInfo.gameId
        currentPlayer = gameInfo.currentPlayer
        turns = gameInfo.turns
        turnNotification = true

        // We use the long way and not gameInfo.currentPlayerCiv to get the currentCiv
        // as this allows us to create a MultiplayerGameInfo based of
        // uninitialized GameInfo (no transient data)
        // This is essential for the MultiplayerTurnChecker
        val currentCiv = gameInfo.civilizations.find { it.civName == gameInfo.currentPlayer }

        currentPlayerId = currentCiv?.playerId!!
    }

    /**
     * Updates the current player in the MultiplayerGameInfo object with the help of a
     * GameInfo object (can be uninitialized). Does not change the multiplayer settings.
     */
    fun updateCurrentPlayer(gameInfo: GameInfo) : MultiplayerGameInfo {
        currentPlayer = gameInfo.currentPlayer
        turns = gameInfo.turns

        // We use the long way and not gameInfo.currentPlayerCiv.playerId to get the playerId
        // as this allows us to create a MultiplayerGameInfo based of
        // uninitialized GameInfo (no transient data) in MultiplayerTurnChecker
        val currentCiv = gameInfo.civilizations.find { it.civName == gameInfo.currentPlayer }

        currentPlayerId = currentCiv?.playerId!!
        return this
    }
}