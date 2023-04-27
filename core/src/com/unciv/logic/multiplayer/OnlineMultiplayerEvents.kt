package com.unciv.logic.multiplayer

import com.unciv.logic.GameInfo
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.event.Event
import com.unciv.logic.multiplayer.apiv2.UpdateGameData

interface HasMultiplayerGameName {
    val name: String
}

interface MultiplayerGameUpdateEnded : Event, HasMultiplayerGameName
interface MultiplayerGameUpdateSucceeded : Event, HasMultiplayerGameName {
    val preview: GameInfoPreview
}

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
    override val preview: GameInfoPreview,
) : MultiplayerGameUpdateEnded, MultiplayerGameUpdateSucceeded

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
    override val preview: GameInfoPreview
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
) : Event, HasMultiplayerGameName

/**
 * Gets sent when a game is deleted
 */
class MultiplayerGameDeleted(
    override val name: String
) : Event, HasMultiplayerGameName

/**
 * Gets sent when [UpdateGameData] has been processed by [OnlineMultiplayer], used to auto-load a game state
 */
class MultiplayerGameCanBeLoaded(
    val gameInfo: GameInfo,
    val gameName: String?, // optionally, the name of the game
    val gameDataID: Long // server data ID for the game state
): Event
