package com.unciv.logic.multiplayer

import com.unciv.logic.GameInfoPreview
import com.unciv.logic.event.Event

/**
 * Gets sent when a game was added.
 */
class MultiplayerGameAdded(
    val name: String
) : Event
/**
 * Gets sent when a game successfully updated
 */
class MultiplayerGameUpdated(
    val name: String,
    val preview: GameInfoPreview,
) : Event

/**
 * Gets sent when a game errored while updating
 */
class MultiplayerGameUpdateErrored(
    val name: String,
    val error: Exception
) : Event
/**
 * Gets sent when a game updated successfully, but nothing changed
 */
class MultiplayerGameUpdateUnchanged(
    val name: String
) : Event

/**
 * Gets sent when a game starts updating
 */
class MultiplayerGameUpdateStarted(
    val name: String
) : Event

/**
 * Gets sent when a game's name got changed
 */
class MultiplayerGameNameChanged(
    val name: String,
    val oldName: String
) : Event

/**
 * Gets sent when a game is deleted
 */
class MultiplayerGameDeleted(
    val name: String
) : Event
