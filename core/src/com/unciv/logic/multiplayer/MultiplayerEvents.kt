package com.unciv.logic.multiplayer

import com.unciv.UncivGame
import com.unciv.logic.HasGameId
import com.unciv.logic.event.Event
import com.unciv.logic.multiplayer.Multiplayer.GameStatus

interface HasMultiplayerGame {
    val game: MultiplayerGame
}

/** Gets sent when a game's data has changed */
interface MultiplayerGameChanged : Event, HasMultiplayerGame

interface MultiplayerGameUpdateEnded : Event, HasMultiplayerGame
interface MultiplayerGameUpdateSucceeded : MultiplayerGameChanged {
    val status: GameStatus
}

/**
 * Gets sent when a game was added.
 */
class MultiplayerGameAdded(
    override val game: MultiplayerGame
) : MultiplayerGameChanged
/**
 * Gets sent when a game successfully updated
 */
class MultiplayerGameUpdated(
    override val game: MultiplayerGame,
    override val status: GameStatus
) : MultiplayerGameChanged, MultiplayerGameUpdateEnded, MultiplayerGameUpdateSucceeded

/**
 * Gets sent when a game errored while updating
 */
class MultiplayerGameUpdateFailed(
    override val game: MultiplayerGame,
    val error: Exception
) : MultiplayerGameUpdateEnded
/**
 * Gets sent when a game updated successfully, but nothing changed
 */
class MultiplayerGameUpdateUnchanged(
    override val game: MultiplayerGame,
    override val status: GameStatus
) : MultiplayerGameUpdateEnded, MultiplayerGameUpdateSucceeded

/**
 * Gets sent when a game starts updating
 */
class MultiplayerGameUpdateStarted(
    override val game: MultiplayerGame,
) : Event, HasMultiplayerGame

/**
 * Gets sent when a game's name got changed
 */
class MultiplayerGameNameChanged(
    override val game: MultiplayerGame,
    val oldName: String,
) : MultiplayerGameChanged

/**
 * Gets sent when a game is deleted
 */
class MultiplayerGameDeleted(
    override val game: MultiplayerGame
) : MultiplayerGameChanged

