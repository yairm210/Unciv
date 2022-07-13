package com.unciv.logic.multiplayer

import com.unciv.logic.event.Event
import com.unciv.logic.multiplayer.Multiplayer.GameStatus

interface HasMultiplayerGameName {
    val name: String
}

/** Gets sent when a game's data has changed */
interface MultiplayerGameChanged : Event, HasMultiplayerGameName

interface MultiplayerGameUpdateEnded : Event, HasMultiplayerGameName
interface MultiplayerGameUpdateSucceeded : MultiplayerGameChanged {
    val status: GameStatus
}

/**
 * Gets sent when a game was added.
 */
class MultiplayerGameAdded(
    override val name: String
) : MultiplayerGameChanged
/**
 * Gets sent when a game successfully updated
 */
class MultiplayerGameUpdated(
    override val name: String,
    override val status: GameStatus,
) : MultiplayerGameChanged, MultiplayerGameUpdateEnded, MultiplayerGameUpdateSucceeded

/**
 * Gets sent when a game errored while updating
 */
class MultiplayerGameUpdateFailed(
    override val name: String,
    val error: Exception
) : MultiplayerGameUpdateEnded
/**
 * Gets sent when a game updated successfully, but nothing changed
 */
class MultiplayerGameUpdateUnchanged(
    override val name: String,
    override val status: GameStatus
) : MultiplayerGameUpdateEnded, MultiplayerGameUpdateSucceeded

/**
 * Gets sent when a game starts updating
 */
class MultiplayerGameUpdateStarted(
    override val name: String
) : Event, HasMultiplayerGameName

/**
 * Gets sent when a game's name got changed
 */
class MultiplayerGameNameChanged(
    override val name: String,
    val newName: String
) : MultiplayerGameChanged

/**
 * Gets sent when a game is deleted
 */
class MultiplayerGameDeleted(
    override val name: String
) : MultiplayerGameChanged

