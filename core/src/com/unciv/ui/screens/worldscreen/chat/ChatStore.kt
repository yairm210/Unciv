package com.unciv.ui.screens.worldscreen.chat

object ChatStore {
    // Key: GameId, Value: MutableList of String, String pairs
    // Each pair is civName and message
    // Chat does not need to be stored and just need to survive screen changes
    private val gameIdToMessages = mutableMapOf<String, MutableList<Pair<String, String>>>()

    fun getMessagesByGameId(gameId: String) = gameIdToMessages.getOrPut(gameId) { mutableListOf() }
}
