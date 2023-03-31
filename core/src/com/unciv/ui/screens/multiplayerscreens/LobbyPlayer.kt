package com.unciv.ui.screens.multiplayerscreens

import com.unciv.Constants
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.multiplayer.apiv2.AccountResponse
import com.unciv.models.metadata.Player

/**
 * A single player in a lobby for APIv2 games (with support for AI players)
 *
 * The goal is to be compatible with [Player], but don't extend it or
 * implement a common interface, since this would decrease chances of
 * easy backward compatibility without any further modifications.
 * Human players are identified by a valid [account], use null for AI players.
 */
class LobbyPlayer(internal val account: AccountResponse?, var chosenCiv: String = Constants.random) {
    val isAI: Boolean
        get() = account == null

    fun to() = Player().apply {
        playerType = PlayerType.AI
        if (!isAI) {
            playerType = PlayerType.Human
            playerId = account!!.uuid.toString()
        }
    }
}
