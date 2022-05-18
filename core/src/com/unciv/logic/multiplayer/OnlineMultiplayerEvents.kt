package com.unciv.logic.multiplayer

import com.unciv.logic.GameInfoPreview
import com.unciv.logic.event.Event

interface HasMultiplayerGameName {
    val name: String
}

interface MultiplayerGameUpdateEnded : Event, HasMultiplayerGameName

/**
 * Gets sent when a game was added.
 */
class MultiplayerGameAdded(
    override val name: String
) : Event, HasMultiplayerGameName
/**
 * Gets sent when a game successfully updated
 */
class MultiplayerGameUpdated(
    override val name: String,
    val preview: GameInfoPreview,
) : MultiplayerGameUpdateEnded

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
    override val name: String
) : MultiplayerGameUpdateEnded

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
    val oldName: String
) : Event, HasMultiplayerGameName

/**
 * Gets sent when a game is deleted
 */
class MultiplayerGameDeleted(
    override val name: String
) : Event, HasMultiplayerGameName
